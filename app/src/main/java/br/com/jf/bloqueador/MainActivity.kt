package br.com.jf.bloqueador

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var roleStatus: TextView
    private lateinit var roleButton: Button
    private lateinit var contactsStatus: TextView
    private lateinit var contactsButton: Button
    private lateinit var stats: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildUi(): ScrollView {
        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Bloqueia ligações de números que não estão na sua agenda."
            setPadding(0, dp(4), 0, dp(16))
        })

        // 1. Papel de triagem
        root.addView(section("1. Triagem de chamadas"))
        roleStatus = TextView(this)
        root.addView(roleStatus)
        roleButton = Button(this).apply {
            text = "Ativar triagem"
            setOnClickListener { requestScreeningRole() }
        }
        root.addView(roleButton)

        // 2. Contatos
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

        // 3. Regras
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
        val rejectId = 1001
        val silenceId = 1002
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

        // Estatísticas
        root.addView(section("Bloqueadas"))
        stats = TextView(this)
        root.addView(stats)

        return ScrollView(this).apply {
            addView(root, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }

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

        val last = prefs.lastBlocked
        stats.text = if (last == null) {
            "Nenhuma ligação bloqueada ainda."
        } else {
            val quando = DateFormat.format("dd/MM HH:mm", prefs.lastBlockedAt)
            "Total: ${prefs.blockedCount}\nÚltima: $last em $quando"
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
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
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
