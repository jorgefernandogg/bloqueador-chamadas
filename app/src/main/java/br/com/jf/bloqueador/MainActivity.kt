package br.com.jf.bloqueador

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseExpandableListAdapter
import android.widget.Button
import android.widget.ExpandableListView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var store: HistoryStore
    private lateinit var list: ExpandableListView
    private lateinit var adapter: HistoryAdapter
    private lateinit var roleStatus: TextView
    private lateinit var roleButton: Button
    private lateinit var contactsStatus: TextView
    private lateinit var contactsButton: Button
    private lateinit var historySummary: TextView

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        store = HistoryStore(this)

        list = ExpandableListView(this).apply {
            // Proteção contra tapjacking: ignora toques quando outra janela cobre o app.
            filterTouchesWhenObscured = true
            clipToPadding = false
            addHeaderView(buildHeader(), null, false)
        }
        applySystemBarInsets(list)
        adapter = HistoryAdapter()
        list.setAdapter(adapter)
        setContentView(list)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        store.close()
        super.onDestroy()
    }

    // ---------- Cabeçalho (configurações) ----------

    private fun buildHeader(): View {
        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, dp(8))
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Bloqueia ligações de números que não estão na sua agenda."
            setPadding(0, dp(4), 0, dp(8))
        })

        root.addView(section("1. Triagem de chamadas"))
        roleStatus = TextView(this)
        root.addView(roleStatus)
        roleButton = Button(this).apply {
            text = "Ativar triagem"
            setOnClickListener { requestScreeningRole() }
        }
        root.addView(roleButton)

        root.addView(section("2. Acesso aos contatos"))
        contactsStatus = TextView(this)
        root.addView(contactsStatus)
        contactsButton = Button(this).apply {
            text = "Permitir contatos"
            setOnClickListener {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
            }
        }
        root.addView(contactsButton)

        root.addView(section("3. Regras"))
        root.addView(Switch(this).apply {
            text = "Bloqueio ligado"
            isChecked = prefs.enabled
            setOnCheckedChangeListener { _, c -> prefs.enabled = c }
            setPadding(0, dp(8), 0, dp(8))
        })
        root.addView(Switch(this).apply {
            text = "Bloquear número oculto"
            isChecked = prefs.blockHidden
            setOnCheckedChangeListener { _, c -> prefs.blockHidden = c }
            setPadding(0, dp(8), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "Quando bloquear:"
            setPadding(0, dp(8), 0, 0)
        })
        val rejectId = View.generateViewId()
        val silenceId = View.generateViewId()
        root.addView(RadioGroup(this).apply {
            addView(RadioButton(context).apply {
                id = rejectId
                text = "Rejeitar (a ligação cai na hora)"
            })
            addView(RadioButton(context).apply {
                id = silenceId
                text = "Silenciar (toca sem som e fica no histórico)"
            })
            check(if (prefs.rejectMode) rejectId else silenceId)
            setOnCheckedChangeListener { _, id -> prefs.rejectMode = id == rejectId }
        })

        root.addView(section("Ligações bloqueadas"))
        historySummary = TextView(this)
        root.addView(historySummary)
        return root
    }

    // ---------- Dados ----------

    private fun refresh() {
        val roleManager = getSystemService(RoleManager::class.java)
        val hasRole = roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        roleStatus.text = if (hasRole) "✅ Ativa" else "❌ Inativa — toque em Ativar triagem"
        roleButton.isEnabled = !hasRole

        val hasContacts = checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        contactsStatus.text = if (hasContacts) "✅ Permitido"
        else "❌ Sem acesso — sem isso nenhuma ligação é bloqueada"
        contactsButton.isEnabled = !hasContacts

        val (days, allowed) = try {
            store.purge(System.currentTimeMillis())
            HistoryGrouping.group(store.events(), zone, HistoryStore::sameNumber) to store.allowed()
        } catch (t: Throwable) {
            emptyList<DayGroup>() to emptyList<String>()
        }
        val total = days.sumOf { it.total }
        historySummary.text = if (total == 0) {
            "Nenhuma ligação bloqueada nos últimos ${HistoryStore.RETENTION_DAYS} dias."
        } else {
            "$total nos últimos ${HistoryStore.RETENTION_DAYS} dias. Toque no dia para ver os números."
        }
        adapter.update(days, allowed)
    }

    private fun liberar(number: String) {
        try {
            store.allow(number)
        } catch (t: Throwable) {
            Toast.makeText(this, "Não foi possível liberar o número", Toast.LENGTH_SHORT).show()
            return
        }
        refresh()
        Toast.makeText(this, "Número liberado", Toast.LENGTH_SHORT).show()
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
            putExtra(
                ContactsContract.Intents.Insert.PHONE_TYPE,
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            )
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "App de contatos não encontrado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun remover(number: String) {
        try {
            store.disallow(number)
        } catch (_: Throwable) {
        }
        refresh()
    }

    // ---------- Sanfona ----------

    private inner class HistoryAdapter : BaseExpandableListAdapter() {
        private var days: List<DayGroup> = emptyList()
        private var allowed: List<String> = emptyList()

        fun update(newDays: List<DayGroup>, newAllowed: List<String>) {
            days = newDays
            allowed = newAllowed
            notifyDataSetChanged()
        }

        private fun isAllowedGroup(group: Int) = group == days.size

        private fun isLiberado(number: String) =
            allowed.any { HistoryStore.sameNumber(it, number) }

        override fun getGroupCount() = days.size + 1 // + "Números liberados"
        override fun getChildrenCount(group: Int) =
            if (isAllowedGroup(group)) allowed.size else days[group].entries.size
        override fun getGroup(group: Int): Any =
            if (isAllowedGroup(group)) allowed else days[group]
        override fun getChild(group: Int, child: Int): Any =
            if (isAllowedGroup(group)) allowed[child] else days[group].entries[child]
        override fun getGroupId(group: Int) = group.toLong()
        override fun getChildId(group: Int, child: Int) = group * 10_000L + child
        override fun hasStableIds() = false
        override fun isChildSelectable(group: Int, child: Int) = false

        override fun getGroupView(
            group: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup?
        ): View {
            val (label, count) = if (isAllowedGroup(group)) {
                "Números liberados" to allowed.size
            } else {
                HistoryGrouping.dayLabel(days[group].day, LocalDate.now(zone)) to days[group].total
            }
            return LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(48), dp(14), dp(20), dp(14))
                addView(TextView(context).apply {
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    setTypeface(typeface, Typeface.BOLD)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    text = count.toString()
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    setTypeface(typeface, Typeface.BOLD)
                })
            }
        }

        override fun getChildView(
            group: Int, child: Int, isLast: Boolean, convertView: View?, parent: ViewGroup?
        ): View {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(56), dp(6), dp(20), dp(6))
            }
            val info = TextView(this@MainActivity)
            row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            if (isAllowedGroup(group)) {
                val number = allowed[child]
                info.text = format(number)
                row.addView(actionButton("Remover", "Remover $number") { remover(number) })
                return row
            }

            val e = days[group].entries[child]
            val hora = Instant.ofEpochMilli(e.lastTs).atZone(zone).format(timeFmt)
            info.text = "${if (e.isHidden) "Número oculto" else format(e.number!!)}\n" +
                "${e.count}x · última às $hora"
            when {
                e.isHidden -> Unit
                isLiberado(e.number!!) -> row.addView(TextView(this@MainActivity).apply {
                    text = "Liberado"
                    setTypeface(typeface, Typeface.ITALIC)
                })
                else -> {
                    val number = e.number
                    row.addView(actionButton("Liberar", "Liberar $number") { liberar(number) })
                }
            }
            return row
        }

        private fun actionButton(label: String, description: String, onClick: () -> Unit) =
            Button(this@MainActivity).apply {
                text = label
                contentDescription = description
                isFocusable = false // mantém o cabeçalho do dia clicável
                setOnClickListener { onClick() }
            }
    }

    // ---------- Utilitários ----------

    private fun format(number: String): String =
        PhoneNumberUtils.formatNumber(number, "BR") ?: number

    @Suppress("DEPRECATION")
    private fun applySystemBarInsets(view: View) {
        // targetSdk 35+: o app desenha atrás das barras do sistema; compensa com padding.
        view.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(b.left, b.top, b.right, b.bottom)
            } else {
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }
    }

    @Suppress("DEPRECATION")
    private fun requestScreeningRole() {
        val roleManager = getSystemService(RoleManager::class.java) ?: return
        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            startActivityForResult(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
                REQ_ROLE
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        refresh()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun section(title: String) = TextView(this).apply {
        text = title
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(4))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQ_ROLE = 1
        const val REQ_CONTACTS = 2
    }
}
