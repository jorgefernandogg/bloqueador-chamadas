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
adb install -r "$APK"

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

echo "✅ E2E OK: desconhecido bloqueado, contato liberado"
