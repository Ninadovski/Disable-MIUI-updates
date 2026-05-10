package com.firstgoy.fuckmiuiupdates

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.DataOutputStream

class MainActivity : AppCompatActivity() {
    private val TAG = "MIUIKiller"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnDisable).setOnClickListener {
            val allPkgs = listOf("com.android.updater", "com.miui.updater", "com.miui.android.updater")
            val installed = allPkgs.filter { isPackageInstalled(it) }
            
            if (installed.isEmpty()) {
                Toast.makeText(this, "Пакеты обновления не найдены", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val commands = mutableListOf<String>()
            for (pkg in installed) {
                commands.add("pm hide $pkg")
                commands.add("appops set $pkg POST_NOTIFICATION ignore")
                commands.add("am force-stop $pkg")
            }
            runRootCommandInBackground(commands)
        }

        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            val allPkgs = listOf("com.android.updater", "com.miui.updater", "com.miui.android.updater")
            val commands = mutableListOf<String>()
            for (pkg in allPkgs) {
                if (isPackageInstalled(pkg)) {
                    commands.add("pm unhide $pkg")
                    commands.add("cmd package install-existing $pkg")
                    commands.add("pm enable $pkg")
                    commands.add("appops set $pkg POST_NOTIFICATION allow")
                }
            }
            runRootCommandInBackground(commands, ignoreErrors = true)
        }

        findViewById<Button>(R.id.btnDisableAccount).setOnClickListener {
            // Google Play Services и Framework
            val allPkgs = listOf("com.google.android.gms", "com.google.android.gsf")
            val installed = allPkgs.filter { isPackageInstalled(it) }

            if (installed.isEmpty()) {
                Toast.makeText(this, "Сервисы Google не найдены", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val commands = mutableListOf<String>()
            for (pkg in installed) {
                // Для Google Services НЕ используем pm hide, чтобы не "окирпичить" приложения
                // Используем блокировку уведомлений через AppOps - это безопаснее и эффективнее для уведомлений
                commands.add("appops set $pkg POST_NOTIFICATION ignore")
                commands.add("am force-stop $pkg")
            }
            runRootCommandInBackground(commands)
        }

        findViewById<Button>(R.id.btnEnableAccount).setOnClickListener {
            val allPkgs = listOf("com.google.android.gms", "com.google.android.gsf")
            val commands = mutableListOf<String>()
            for (pkg in allPkgs) {
                if (isPackageInstalled(pkg)) {
                    commands.add("appops set $pkg POST_NOTIFICATION allow")
                }
            }
            runRootCommandInBackground(commands, ignoreErrors = true)
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun runRootCommandInBackground(commands: List<String>, ignoreErrors: Boolean = false) {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                val errorReader = process.errorStream.bufferedReader()
                val inputReader = process.inputStream.bufferedReader()

                for (cmd in commands) {
                    Log.d(TAG, "Executing: $cmd")
                    os.writeBytes(cmd + "\n")
                }

                os.writeBytes("exit\n")
                os.flush()

                val stdOut = StringBuilder()
                val stdErr = StringBuilder()

                val exitVal = process.waitFor()
                
                while (inputReader.ready()) stdOut.append(inputReader.readLine()).append("\n")
                while (errorReader.ready()) stdErr.append(errorReader.readLine()).append("\n")

                Log.d(TAG, "Exit Code: $exitVal")
                if (stdOut.isNotEmpty()) Log.d(TAG, "STDOUT: $stdOut")
                if (stdErr.isNotEmpty()) Log.e(TAG, "STDERR: $stdErr")

                runOnUiThread {
                    if (exitVal == 0 || ignoreErrors) {
                        Toast.makeText(this, "Выполнено успешно", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Код $exitVal. См. Logcat", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Root Error", e)
                runOnUiThread {
                    Toast.makeText(this, "Критическая ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
