package dev.medzik.librepass.android.ui.screens.dashboard.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.medzik.android.composables.TopBar
import dev.medzik.android.composables.TopBarBackIcon
import dev.medzik.android.composables.dialog.PickerDialog
import dev.medzik.android.composables.dialog.rememberDialogState
import dev.medzik.android.composables.settings.SettingsProperty
import dev.medzik.android.composables.settings.SettingsSwitcher
import dev.medzik.android.cryptoutils.KeyStore
import dev.medzik.librepass.android.MainActivity
import dev.medzik.librepass.android.R
import dev.medzik.librepass.android.data.getRepository
import dev.medzik.librepass.android.utils.Biometric
import dev.medzik.librepass.android.utils.SecretStore.getUserSecrets
import dev.medzik.librepass.android.utils.SecretStore.readKey
import dev.medzik.librepass.android.utils.SecretStore.writeKey
import dev.medzik.librepass.android.utils.StoreKey
import dev.medzik.librepass.android.utils.VaultTimeoutValues
import kotlinx.coroutines.launch

@Composable
fun SettingsSecurity(navController: NavController) {
    val context = LocalContext.current

    val userSecrets = context.getUserSecrets() ?: return
    val repository = context.getRepository()
    val credentials = repository.credentials.get()!!

    val scope = rememberCoroutineScope()
    var biometricEnabled by remember { mutableStateOf(credentials.biometricEnabled) }
    val timerDialogState = rememberDialogState()
    var vaultTimeout by remember { mutableIntStateOf(context.readKey(StoreKey.VaultTimeout)) }

    // Biometric checked event handler (enable/disable biometric authentication)
    fun showBiometricPrompt() {
        if (biometricEnabled) {
            biometricEnabled = false

            scope.launch {
                repository.credentials.update(
                    credentials.copy(
                        biometricEnabled = false
                    )
                )
            }

            return
        }

        Biometric.showBiometricPrompt(
            context = context as MainActivity,
            cipher = KeyStore.initCipherForEncryption(
                Biometric.PrivateKeyAlias,
                true
            ),
            onAuthenticationSucceeded = { cipher ->
                val encryptedData = KeyStore.encrypt(
                    cipher = cipher,
                    data = userSecrets.privateKey
                )

                biometricEnabled = true

                scope.launch {
                    repository.credentials.update(
                        credentials.copy(
                            biometricEnabled = true,
                            biometricProtectedPrivateKey = encryptedData.cipherText,
                            biometricProtectedPrivateKeyIV = encryptedData.initializationVector
                        )
                    )
                }
            },
            onAuthenticationFailed = {}
        )
    }

    @Composable
    fun getVaultTimeoutTranslation(value: VaultTimeoutValues): String {
        return when (value) {
            VaultTimeoutValues.INSTANT -> stringResource(R.string.Settings_Vault_Timeout_Instant)
            VaultTimeoutValues.ONE_MINUTE -> pluralStringResource(
                R.plurals.Time_Minutes,
                1,
                1
            )

            VaultTimeoutValues.FIVE_MINUTES -> pluralStringResource(
                R.plurals.Time_Minutes,
                5,
                5
            )

            VaultTimeoutValues.FIFTEEN_MINUTES -> pluralStringResource(
                R.plurals.Time_Minutes,
                15,
                15
            )

            VaultTimeoutValues.THIRTY_MINUTES -> pluralStringResource(
                R.plurals.Time_Minutes,
                30,
                30
            )

            VaultTimeoutValues.ONE_HOUR -> pluralStringResource(
                R.plurals.Time_Hours,
                1,
                1
            )

            VaultTimeoutValues.NEVER -> stringResource(R.string.Settings_Vault_Timeout_Never)
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = R.string.Settings_Group_Security,
                navigationIcon = { TopBarBackIcon(navController) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            SettingsSwitcher(
                icon = Icons.Default.Fingerprint,
                resId = R.string.Settings_BiometricUnlock,
                checked = biometricEnabled,
                onCheckedChange = { showBiometricPrompt() }
            )

            SettingsProperty(
                icon = Icons.Default.Timer,
                resId = R.string.Settings_Vault_Timeout_Modal_Title,
                currentValue = getVaultTimeoutTranslation(
                    VaultTimeoutValues.fromSeconds(
                        vaultTimeout
                    )
                ),
                onClick = { timerDialogState.show() },
            )

            PickerDialog(
                state = timerDialogState,
                title = R.string.Settings_Vault_Timeout_Modal_Title,
                items = VaultTimeoutValues.values().asList(),
                onSelected = {
                    vaultTimeout = it.seconds
                    context.writeKey(StoreKey.VaultTimeout, it.seconds)
                }
            ) {
                Text(
                    text = getVaultTimeoutTranslation(it),
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .fillMaxWidth()
                )
            }
        }
    }
}
