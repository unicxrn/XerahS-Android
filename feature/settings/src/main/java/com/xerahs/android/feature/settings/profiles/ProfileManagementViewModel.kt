package com.xerahs.android.feature.settings.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.common.generateId
import com.xerahs.android.core.common.generateTimestamp
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadProfile
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileListUiState(
    val profiles: List<UploadProfile> = emptyList(),
    val isLoading: Boolean = true
)

data class ProfileEditorUiState(
    val profileId: String? = null,
    val name: String = "",
    val destination: UploadDestination = UploadDestination.IMGUR,
    val isDefault: Boolean = false,
    val isEditing: Boolean = false,
    // Imgur
    val imgurClientId: String = "",
    val imgurClientSecret: String = "",
    val imgurUseAnonymous: Boolean = true,
    // S3
    val s3AccessKeyId: String = "",
    val s3SecretAccessKey: String = "",
    val s3Region: String = "us-east-1",
    val s3Bucket: String = "",
    val s3Endpoint: String = "",
    val s3CustomUrl: String = "",
    val s3Prefix: String = "",
    val s3Acl: String = "",
    val s3UsePathStyle: Boolean = false,
    // FTP
    val ftpHost: String = "",
    val ftpPort: String = "21",
    val ftpUsername: String = "",
    val ftpPassword: String = "",
    val ftpRemotePath: String = "/",
    val ftpUseFtps: Boolean = false,
    val ftpUsePassive: Boolean = true,
    val ftpHttpUrl: String = "",
    // SFTP
    val sftpHost: String = "",
    val sftpPort: String = "22",
    val sftpUsername: String = "",
    val sftpPassword: String = "",
    val sftpKeyPath: String = "",
    val sftpKeyPassphrase: String = "",
    val sftpRemotePath: String = "/",
    val sftpHttpUrl: String = "",
    // Custom HTTP
    val customHttpUrl: String = "",
    val customHttpMethod: String = "POST",
    val customHttpHeaders: String = "",
    val customHttpJsonPath: String = "url",
    val customHttpFormField: String = "file",
    // UI
    val isSaving: Boolean = false
)

@HiltViewModel
class ProfileManagementViewModel @Inject constructor(
    private val profileRepository: UploadProfileRepository
) : ViewModel() {

    private val _listState = MutableStateFlow(ProfileListUiState())
    val listState: StateFlow<ProfileListUiState> = _listState.asStateFlow()

    private val _editorState = MutableStateFlow(ProfileEditorUiState())
    val editorState: StateFlow<ProfileEditorUiState> = _editorState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.getAllProfiles().collect { profiles ->
                _listState.value = ProfileListUiState(profiles = profiles, isLoading = false)
            }
        }
    }

    fun startNewProfile() {
        _editorState.value = ProfileEditorUiState()
    }

    fun startEditProfile(profileId: String) {
        viewModelScope.launch {
            val profile = profileRepository.getProfile(profileId) ?: return@launch
            val config = profileRepository.getProfileConfig(profileId, profile.destination)

            _editorState.value = ProfileEditorUiState(
                profileId = profileId,
                name = profile.name,
                destination = profile.destination,
                isDefault = profile.isDefault,
                isEditing = true
            ).applyConfig(config)
        }
    }

    fun updateEditorName(name: String) {
        _editorState.value = _editorState.value.copy(name = name)
    }

    fun updateEditorDestination(destination: UploadDestination) {
        _editorState.value = _editorState.value.copy(destination = destination)
    }

    fun updateEditorDefault(isDefault: Boolean) {
        _editorState.value = _editorState.value.copy(isDefault = isDefault)
    }

    // Imgur setters
    fun updateImgurClientId(v: String) { _editorState.value = _editorState.value.copy(imgurClientId = v) }
    fun updateImgurClientSecret(v: String) { _editorState.value = _editorState.value.copy(imgurClientSecret = v) }
    fun updateImgurUseAnonymous(v: Boolean) { _editorState.value = _editorState.value.copy(imgurUseAnonymous = v) }

    // S3 setters
    fun updateS3AccessKeyId(v: String) { _editorState.value = _editorState.value.copy(s3AccessKeyId = v) }
    fun updateS3SecretAccessKey(v: String) { _editorState.value = _editorState.value.copy(s3SecretAccessKey = v) }
    fun updateS3Region(v: String) { _editorState.value = _editorState.value.copy(s3Region = v) }
    fun updateS3Bucket(v: String) { _editorState.value = _editorState.value.copy(s3Bucket = v) }
    fun updateS3Endpoint(v: String) { _editorState.value = _editorState.value.copy(s3Endpoint = v) }
    fun updateS3CustomUrl(v: String) { _editorState.value = _editorState.value.copy(s3CustomUrl = v) }
    fun updateS3Prefix(v: String) { _editorState.value = _editorState.value.copy(s3Prefix = v) }
    fun updateS3Acl(v: String) { _editorState.value = _editorState.value.copy(s3Acl = v) }
    fun updateS3UsePathStyle(v: Boolean) { _editorState.value = _editorState.value.copy(s3UsePathStyle = v) }

    // FTP setters
    fun updateFtpHost(v: String) { _editorState.value = _editorState.value.copy(ftpHost = v) }
    fun updateFtpPort(v: String) { _editorState.value = _editorState.value.copy(ftpPort = v) }
    fun updateFtpUsername(v: String) { _editorState.value = _editorState.value.copy(ftpUsername = v) }
    fun updateFtpPassword(v: String) { _editorState.value = _editorState.value.copy(ftpPassword = v) }
    fun updateFtpRemotePath(v: String) { _editorState.value = _editorState.value.copy(ftpRemotePath = v) }
    fun updateFtpUseFtps(v: Boolean) { _editorState.value = _editorState.value.copy(ftpUseFtps = v) }
    fun updateFtpUsePassive(v: Boolean) { _editorState.value = _editorState.value.copy(ftpUsePassive = v) }
    fun updateFtpHttpUrl(v: String) { _editorState.value = _editorState.value.copy(ftpHttpUrl = v) }

    // SFTP setters
    fun updateSftpHost(v: String) { _editorState.value = _editorState.value.copy(sftpHost = v) }
    fun updateSftpPort(v: String) { _editorState.value = _editorState.value.copy(sftpPort = v) }
    fun updateSftpUsername(v: String) { _editorState.value = _editorState.value.copy(sftpUsername = v) }
    fun updateSftpPassword(v: String) { _editorState.value = _editorState.value.copy(sftpPassword = v) }
    fun updateSftpKeyPath(v: String) { _editorState.value = _editorState.value.copy(sftpKeyPath = v) }
    fun updateSftpKeyPassphrase(v: String) { _editorState.value = _editorState.value.copy(sftpKeyPassphrase = v) }
    fun updateSftpRemotePath(v: String) { _editorState.value = _editorState.value.copy(sftpRemotePath = v) }
    fun updateSftpHttpUrl(v: String) { _editorState.value = _editorState.value.copy(sftpHttpUrl = v) }

    // Custom HTTP setters
    fun updateCustomHttpUrl(v: String) { _editorState.value = _editorState.value.copy(customHttpUrl = v) }
    fun updateCustomHttpMethod(v: String) { _editorState.value = _editorState.value.copy(customHttpMethod = v) }
    fun updateCustomHttpHeaders(v: String) { _editorState.value = _editorState.value.copy(customHttpHeaders = v) }
    fun updateCustomHttpJsonPath(v: String) { _editorState.value = _editorState.value.copy(customHttpJsonPath = v) }
    fun updateCustomHttpFormField(v: String) { _editorState.value = _editorState.value.copy(customHttpFormField = v) }

    fun saveProfile(onComplete: () -> Unit) {
        viewModelScope.launch {
            _editorState.value = _editorState.value.copy(isSaving = true)
            val state = _editorState.value
            val id = state.profileId ?: generateId()

            val profile = UploadProfile(
                id = id,
                name = state.name.ifBlank { "${state.destination.displayName} Profile" },
                destination = state.destination,
                isDefault = state.isDefault,
                createdAt = generateTimestamp()
            )

            val config = state.toConfig()

            if (state.isEditing) {
                profileRepository.updateProfile(profile, config)
            } else {
                profileRepository.createProfile(profile, config)
            }

            if (state.isDefault) {
                profileRepository.setDefault(id, state.destination)
            }

            _editorState.value = _editorState.value.copy(isSaving = false)
            onComplete()
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.deleteProfile(profileId)
        }
    }

    private fun ProfileEditorUiState.applyConfig(config: UploadConfig): ProfileEditorUiState {
        return when (config) {
            is UploadConfig.ImgurConfig -> copy(
                imgurClientId = config.clientId,
                imgurClientSecret = config.clientSecret,
                imgurUseAnonymous = config.useAnonymous
            )
            is UploadConfig.S3Config -> copy(
                s3AccessKeyId = config.accessKeyId,
                s3SecretAccessKey = config.secretAccessKey,
                s3Region = config.region,
                s3Bucket = config.bucket,
                s3Endpoint = config.endpoint ?: "",
                s3CustomUrl = config.customUrl ?: "",
                s3Prefix = config.prefix,
                s3Acl = config.acl,
                s3UsePathStyle = config.usePathStyle
            )
            is UploadConfig.FtpConfig -> copy(
                ftpHost = config.host,
                ftpPort = config.port.toString(),
                ftpUsername = config.username,
                ftpPassword = config.password,
                ftpRemotePath = config.remotePath,
                ftpUseFtps = config.useFtps,
                ftpUsePassive = config.usePassiveMode,
                ftpHttpUrl = config.httpUrl
            )
            is UploadConfig.SftpConfig -> copy(
                sftpHost = config.host,
                sftpPort = config.port.toString(),
                sftpUsername = config.username,
                sftpPassword = config.password,
                sftpKeyPath = config.keyPath ?: "",
                sftpKeyPassphrase = config.keyPassphrase ?: "",
                sftpRemotePath = config.remotePath,
                sftpHttpUrl = config.httpUrl
            )
            is UploadConfig.CustomHttpConfig -> copy(
                customHttpUrl = config.url,
                customHttpMethod = config.method,
                customHttpHeaders = config.headers.entries.joinToString("\n") { "${it.key}=${it.value}" },
                customHttpJsonPath = config.responseUrlJsonPath,
                customHttpFormField = config.formFieldName
            )
        }
    }

    private fun ProfileEditorUiState.toConfig(): UploadConfig {
        return when (destination) {
            UploadDestination.IMGUR -> UploadConfig.ImgurConfig(
                clientId = imgurClientId,
                clientSecret = imgurClientSecret,
                useAnonymous = imgurUseAnonymous
            )
            UploadDestination.S3 -> UploadConfig.S3Config(
                accessKeyId = s3AccessKeyId,
                secretAccessKey = s3SecretAccessKey,
                region = s3Region,
                bucket = s3Bucket,
                endpoint = s3Endpoint.ifBlank { null },
                customUrl = s3CustomUrl.ifBlank { null },
                prefix = s3Prefix,
                acl = s3Acl,
                usePathStyle = s3UsePathStyle
            )
            UploadDestination.FTP -> UploadConfig.FtpConfig(
                host = ftpHost,
                port = ftpPort.toIntOrNull() ?: 21,
                username = ftpUsername,
                password = ftpPassword,
                remotePath = ftpRemotePath,
                useFtps = ftpUseFtps,
                usePassiveMode = ftpUsePassive,
                httpUrl = ftpHttpUrl
            )
            UploadDestination.SFTP -> UploadConfig.SftpConfig(
                host = sftpHost,
                port = sftpPort.toIntOrNull() ?: 22,
                username = sftpUsername,
                password = sftpPassword,
                keyPath = sftpKeyPath.ifBlank { null },
                keyPassphrase = sftpKeyPassphrase.ifBlank { null },
                remotePath = sftpRemotePath,
                httpUrl = sftpHttpUrl
            )
            UploadDestination.CUSTOM_HTTP -> {
                val headers = customHttpHeaders.lines()
                    .filter { it.contains("=") }
                    .associate { line ->
                        val (key, value) = line.split("=", limit = 2)
                        key to value
                    }
                UploadConfig.CustomHttpConfig(
                    url = customHttpUrl,
                    method = customHttpMethod,
                    headers = headers,
                    responseUrlJsonPath = customHttpJsonPath,
                    formFieldName = customHttpFormField
                )
            }
            UploadDestination.LOCAL -> UploadConfig.S3Config() // Placeholder
        }
    }
}
