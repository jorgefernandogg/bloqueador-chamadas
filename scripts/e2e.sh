#!/usr/bin/env bash
# Teste ponta a ponta no emulador: instala o APK real, concede papel de triagem
# e permissão de contatos, cadastra um contato e simula duas ligações GSM.
# Espera: desconhecido = rejeitada/bloqueada; contato = toca normalmente.
set -euo pipefail

APK="$1"
PKG=br.com.jf.bloqueador
CONTATO=48999990001
DESCONHECIDO=48988887777

fail() {
  echo "::error::$1"
  echo "----- logcat (Bloqueador/Telecom) -----"
  adb logcat -d -s Bloqueador:V Telecom:I | tail -80 || true
  echo "----- dumpsys telecom (histórico) -----"
  adb shell dumpsys telecom | sed -n '/Historical Calls/,/^$/p' | head -80 || true
  exit 1
}

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'

echo "== Instalando APK"
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install "$APK"

echo "== Permissão de contatos"
adb shell pm grant "$PKG" android.permission.READ_CONTACTS

echo "== Papel de triagem"
adb shell cmd role add-role-holder android.app.role.CALL_SCREENING "$PKG"
holders=$(adb shell cmd role get-role-holders android.app.role.CALL_SCREENING | tr -d '\r')
echo "holders: $holders"
[[ "$holders" == *"$PKG"* ]] || fail "papel CALL_SCREENING não foi atribuído"

echo "== Cadastrando contato $CONTATO"
adb shell content insert --uri content://com.android.contacts/raw_contacts \
  --bind account_type:n: --bind account_name:n:
RAW=$(adb shell content query --uri content://com.android.contacts/raw_contacts --projection _id \
  | tr -d '\r' | grep -o '_id=[0-9]*' | tail -1 | cut -d= -f2)
[[ -n "$RAW" ]] || fail "não consegui criar raw_contact"
adb shell content insert --uri content://com.android.contacts/data \
  --bind raw_contact_id:i:"$RAW" --bind mimetype:s:vnd.android.cursor.item/name \
  --bind data1:s:ContatoTeste
adb shell content insert --uri content://com.android.contacts/data \
  --bind raw_contact_id:i:"$RAW" --bind mimetype:s:vnd.android.cursor.item/phone_v2 \
  --bind data1:s:"$CONTATO" --bind data2:i:2
lookup=$(adb shell content query --uri "content://com.android.contacts/phone_lookup/$CONTATO" \
  --projection display_name | tr -d '\r')
echo "lookup: $lookup"
[[ "$lookup" == *ContatoTeste* ]] || fail "contato não aparece no PhoneLookup"

simulate_call() {
  echo "== Ligação simulada de $1"
  adb emu gsm call "$1"
  sleep 10
  adb emu gsm cancel "$1" || true
  sleep 5
}

# Esquenta o Telecom com uma chamada descartável antes de medir.
simulate_call 48900000000
simulate_call "$DESCONHECIDO"
simulate_call "$CONTATO"

echo "== Histórico de chamadas"
log=$(adb shell content query --uri content://call_log/calls --projection number:type | tr -d '\r')
echo "$log"

type_of() {
  echo "$log" | grep "number=$1," | tail -1 | grep -o 'type=[0-9]*' | cut -d= -f2
}

t_desc=$(type_of "$DESCONHECIDO")
t_cont=$(type_of "$CONTATO")
echo "tipo desconhecido=$t_desc contato=$t_cont  (1=recebida 3=perdida 5=rejeitada 6=bloqueada)"

[[ "$t_desc" == "5" || "$t_desc" == "6" ]] || fail "desconhecido NÃO foi bloqueado (type=$t_desc)"
[[ "$t_cont" == "1" || "$t_cont" == "3" ]] || fail "contato foi bloqueado ou não registrado (type=$t_cont)"

echo "✅ Bloqueio OK: desconhecido bloqueado, contato liberado"

# ---------- v2: histórico em sanfona + liberar número pela tela ----------

# Toca no elemento cujo texto ou content-desc é exatamente $1.
tap_ui() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null
  local xy
  xy=$(python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
want = sys.argv[1]
for n in ET.parse('/tmp/ui.xml').iter('node'):
    if n.get('text') == want or n.get('content-desc') == want:
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  [[ -n "$xy" ]] || return 1
  adb shell input tap $xy
}

# Rolagem proporcional ao tamanho real da tela do emulador.
swipe_up() {
  local size w h
  size=$(adb shell wm size | tr -d '\r' | grep -o '[0-9]*x[0-9]*' | tail -1)
  w=${size%x*}; h=${size#*x}
  adb shell input swipe $((w / 2)) $((h * 3 / 4)) $((w / 2)) $((h / 4)) 400
}

# Lista os textos visíveis (diagnóstico quando algo não é encontrado).
dump_texts() {
  python3 - <<'PY3'
import xml.etree.ElementTree as ET
try:
    for n in ET.parse('/tmp/ui.xml').iter('node'):
        t = n.get('text') or n.get('content-desc')
        if t: print('   ui:', t.replace('\n', ' | '))
except Exception as e:
    print('   (sem dump)', e)
PY3
}

# Só verifica se o elemento existe na tela (sem tocar), rolando se preciso.
scroll_find() {
  for _ in $(seq 1 8); do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null
    adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null
    if python3 - "$1" <<'PY2'
import sys, xml.etree.ElementTree as ET
want = sys.argv[1]
sys.exit(0 if any(n.get('text') == want or n.get('content-desc') == want
                  for n in ET.parse('/tmp/ui.xml').iter('node')) else 1)
PY2
    then return 0; fi
    swipe_up
    sleep 1
  done
  dump_texts
  return 1
}

# Rola a lista até achar o elemento e toca nele.
scroll_and_tap() {
  for _ in $(seq 1 8); do
    if tap_ui "$1"; then return 0; fi
    swipe_up
    sleep 1
  done
  dump_texts
  return 1
}

echo "== Abrindo o app"
adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
sleep 3

echo "== Expandindo 'Hoje' na sanfona"
scroll_and_tap "Hoje" || fail "dia 'Hoje' não apareceu no histórico"
sleep 2

echo "== Tocando em Liberar $DESCONHECIDO"
scroll_and_tap "Liberar $DESCONHECIDO" || fail "botão Liberar do número bloqueado não apareceu"
sleep 3

echo "== Conferindo que o app mostra o número em 'Números liberados'"
# Garante que a tela de contato foi aberta com o número preenchido.
scroll_find "$DESCONHECIDO" || fail "tela de novo contato não abriu com o número"
# Fecha o editor de contato sem salvar e reabre o app do zero.
fg=$(adb shell dumpsys window | tr -d '\r' | grep -m1 -oE 'mCurrentFocus=[^ ]+ [^ ]+ [^/ ]+' | awk '{print $NF}')
echo "app em primeiro plano: $fg"
[[ -n "$fg" && "$fg" != "$PKG" ]] && adb shell am force-stop "$fg"
adb shell am force-stop com.android.contacts || true
adb shell am force-stop com.google.android.contacts || true
adb shell am start -S -W -n "$PKG/.MainActivity" >/dev/null
sleep 3
scroll_and_tap "Números liberados" || fail "grupo 'Números liberados' não apareceu"
sleep 2
scroll_find "Remover $DESCONHECIDO" || fail "número liberado não aparece na lista de liberados"
adb shell input keyevent KEYCODE_HOME
sleep 2

simulate_call "$DESCONHECIDO"
log=$(adb shell content query --uri content://call_log/calls --projection number:type | tr -d '\r')
t_lib=$(type_of "$DESCONHECIDO")
echo "tipo após liberar=$t_lib"
[[ "$t_lib" == "1" || "$t_lib" == "3" ]] || fail "número liberado continuou bloqueado (type=$t_lib)"

echo "✅ E2E OK: bloqueio, histórico em sanfona e liberação funcionando"
