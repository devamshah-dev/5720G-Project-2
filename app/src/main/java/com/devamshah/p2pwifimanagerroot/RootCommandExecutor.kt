package com.devamshah.p2pwifimanagerroot

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*

object RootCommandExecutor {
    private const val TAG = "RootCmdExecutor"
    const val SSH_HOST_KEY_PATH = "/data/ssh/ssh_host_rsa_key.pub" // Adjust if your key path is different
    const val SSH_KEYGEN_BINARY = "ssh-keygen"

     //Executes a command with root privileges using 'su -c'.
     //@param command The command string to execute.
     //@param timeoutMs Maximum time to wait for command execution.
     //@return A Triple of Boolean (success), String (stdout), String (stderr).

    fun execute(command: String, timeoutMs: Long = 10000L): Triple<Boolean, String, String> {
        var process: Process? = null
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var success = false

        try {
            Log.d(TAG, "Executing command with root: $command")
            process = Runtime.getRuntime().exec("su") // Get root shell
            val os = OutputStreamWriter(process.outputStream)
            val isr = BufferedReader(InputStreamReader(process.inputStream))
            val esr = BufferedReader(InputStreamReader(process.errorStream))
            os.write(command)
            os.write("\n")
            os.write("exit\n") // Exit 'su'
            os.flush()

            val readOutputJob = Thread {
                var line: String?
                while (isr.readLine().also { line = it } != null) {
                    stdout.append(line).append("\n")
                    Log.v(TAG, "STDOUT: $line")
                }
            }
            val readErrorJob = Thread {
                var line: String?
                while (esr.readLine().also { line = it } != null) {
                    stderr.append(line).append("\n")
                    Log.w(TAG, "STDERR: $line")
                }
            }
            readOutputJob.start()
            readErrorJob.start()

            // timeout for both reader threads to finish
            readOutputJob.join(timeoutMs)
            readErrorJob.join(timeoutMs)

            val exitCode = process.waitFor() // wait for the 'su' process to finish
            if (exitCode == 0) {
                success = true
                Log.d(TAG, "Command success. Output: ${stdout.toString().trim()}")
            } else {
                Log.e(TAG, "Command failed (code $exitCode). Stderr: ${stderr.toString().trim()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during command execution: ${e.message}", e)
            stderr.append("Exception: ${e.message}")
        } finally {
            try {
                process?.destroy() // Ensure the process is cleaned up
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying process: ${e.message}")
            }
        }
        return Triple(success, stdout.toString().trim(), stderr.toString().trim())
    }

    //Retrieves the SHA256 fingerprint of the SSH host public key & ssh-keygen is in the root's PATH (e.g., /system/bin or provided by Magisk).
    fun getSshHostKeyFingerprint(): String? {
         val keyFile = File(SSH_HOST_KEY_PATH)
        if (!keyFile.exists()) {
            Log.e(TAG, "SSH host public key not found at $SSH_HOST_KEY_PATH.")
            return null
        }
        // Use ssh-keygen to get the SHA256 fingerprint
        val (success, stdout, stderr) = execute("ssh-keygen -lf $SSH_HOST_KEY_PATH -E sha256")
        if (success) {
            // format: "256 SHA256:<fingerprint> user@host (RSA)"
            val match = Regex("SHA256:([a-zA-Z0-9/+=]+)").find(stdout)
            return match?.groupValues?.get(1)
        } else {
            Log.e(TAG, "Failed to get SSH host key fingerprint: $stderr")
        }
        return null
    }

    //Gets the local IPv4 address of a network interface (e.g., "wlan0" or "p2p-wlan0-0").
    fun getLocalIpAddress(interfaceName: String): String? {
        try {
            val networkInterface = NetworkInterface.getByName(interfaceName)
            if (networkInterface != null && networkInterface.isUp) {
                val inetAddresses = Collections.list(networkInterface.inetAddresses)
                for (addr in inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            } else {
                Log.w(TAG, "Network interface '$interfaceName' not found or not up.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP for interface '$interfaceName': ${e.message}")
        }
        return null
    }

     // Gets the primary local IPv4 address -> wlan0 & P2P interface name is unknown or not always present.
    fun getPrimaryLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isUp && !intf.isLoopback && !intf.isVirtual) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            Log.d(TAG, "Found primary IP: ${addr.hostAddress} on interface ${intf.name}")
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting primary IP address: ${e.message}")
        }
        return null
    }
}