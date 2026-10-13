package com.fcitx5sensevoice

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class VoiceInputMethodService : InputMethodService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val asrExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "fcitx5-sensevoice-asr")
    }
    private lateinit var statusView: TextView
    private lateinit var voiceWaveView: VoiceWaveView
    private lateinit var microphoneButton: ImageButton
    private lateinit var deleteButton: ImageButton
    private lateinit var hintView: TextView
    private lateinit var loadingSkeleton: View
    private lateinit var loadingSheen: View
    private var sheenAnimator: ObjectAnimator? = null
    private var recordingStartedAtMs = 0L
    private var capturedSamples = 0
    private var recognizedSegments = 0
    private var committedSegments = 0
    private var recordingSessionActive = false
    private var recognizeWhenStopped = false
    private var recordingCancelled = false
    private var asrInitializing = false
    private var asrReady = false
    private var asrInitError: String? = null
    private var loadedAsrSettings: AsrSettings? = null
    private var loadedUiLanguage: UiLanguage? = null
    private var loadedNightMode = -1
    private var asrInitializationGeneration = 0L
    private var inputSession = 0L
    private var recordingGeneration = 0L
    private var inputViewActive = false
    private var voiceState = VoiceState.IDLE
    private var microphonePressed = false
    private var longPressTriggered = false
    private var deletePressed = false
    private var deleteRepeating = false

    private val longPressRunnable = Runnable {
        if (microphonePressed && recorder.state in setOf(AudioRecorderState.IDLE, AudioRecorderState.ERROR)) {
            if (beginRecording()) {
                longPressTriggered = true
                Log.i(TAG, "MIC_GESTURE mode=hold action=start")
                microphoneButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    private val deleteRepeatRunnable = object : Runnable {
        override fun run() {
            if (!deletePressed) return
            if (!deleteRepeating) {
                deleteRepeating = true
                deleteButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                Log.d(TAG, "DELETE_KEY mode=repeat action=start")
            }
            deletePreviousCharacter()
            mainHandler.postDelayed(this, DELETE_REPEAT_INTERVAL_MS)
        }
    }

    @Volatile
    private var destroyed = false

    @Volatile
    private var asrEngine: AsrEngine? = null

    private val recorder by lazy {
        PcmAudioRecorder(
            onStateChanged = { state -> postToMain { onRecorderStateChanged(state) } },
            onError = { error -> postToMain { showRecorderError(error) } },
        )
    }

    // The service's own resources are frozen at creation, so a language or theme change in Settings
    // never reaches them. Derive the UI context from the current preferences on every call.
    private fun uiContext(): Context = wrapUiTheme(ContextCompat.getContextForLanguage(applicationContext))

    override fun onCreate() {
        super.onCreate()
        window?.window?.configureDisplayCutout()
        Log.i(TAG, "IME_CREATED")
        initializeAsr()
    }

    override fun onCreateInputView(): View = inflateInputView()

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshInputViewIfUiChanged()
        inputSession++
        inputViewActive = true
        reloadAsrIfSettingsChanged()
        if (::statusView.isInitialized && recorder.state == AudioRecorderState.IDLE && !recordingSessionActive) {
            renderIdleState()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshInputViewIfUiChanged()
    }

    // Rebuilding the panel would drop an in-progress recording, so defer language/night-mode switches until idle.
    private fun refreshInputViewIfUiChanged() {
        val uiLanguage = currentUiLanguage(this)
        val nightMode = uiContext().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (loadedUiLanguage == uiLanguage && loadedNightMode == nightMode) return
        if (recorder.state != AudioRecorderState.IDLE || recordingSessionActive) return
        loadedUiLanguage = uiLanguage
        loadedNightMode = nightMode
        if (::statusView.isInitialized) setInputView(inflateInputView())
    }

    private fun inflateInputView(): View =
        LayoutInflater.from(uiContext()).inflate(R.layout.input_view, null).also { view ->
            view.applyImePanelInsets()
            statusView = view.findViewById(R.id.status)
            hintView = view.findViewById(R.id.status_hint)
            loadingSkeleton = view.findViewById(R.id.loading_skeleton)
            loadingSheen = view.findViewById(R.id.loading_sheen)
            voiceWaveView = view.findViewById(R.id.voice_wave)
            view.findViewById<ImageButton>(R.id.back).setOnClickListener {
                returnToPreviousInputMethod()
            }
            deleteButton = view.findViewById<ImageButton>(R.id.delete).apply {
                setOnClickListener {
                    deletePreviousCharacter()
                    Log.d(TAG, "DELETE_KEY mode=tap")
                }
                setOnTouchListener(::onDeleteTouch)
            }
            microphoneButton = view.findViewById<ImageButton>(R.id.microphone).apply {
                setOnClickListener { toggleRecording() }
                setOnTouchListener(::onMicrophoneTouch)
            }
            view.findViewById<ImageButton>(R.id.enter).setOnClickListener { sendEnterOrEditorAction() }
            renderIdleState()
        }

    override fun onFinishInputView(finishingInput: Boolean) {
        inputViewActive = false
        inputSession++
        recordingGeneration++
        recognizeWhenStopped = false
        recordingCancelled = false
        stopDeleteRepeat()
        stopSheen()
        recorder.stop()
        resetAsr()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        destroyed = true
        recorder.release()
        asrExecutor.execute {
            asrEngine?.release()
            asrEngine = null
        }
        asrExecutor.shutdown()
        mainHandler.removeCallbacksAndMessages(null)
        Log.i(TAG, "IME_DESTROYED")
        super.onDestroy()
    }

    private fun toggleRecording() {
        when (recorder.state) {
            AudioRecorderState.STARTING,
            AudioRecorderState.RECORDING,
                -> recorder.stop()

            AudioRecorderState.IDLE,
            AudioRecorderState.ERROR,
                -> beginRecording()

            AudioRecorderState.STOPPING -> Unit
        }
    }

    private fun beginRecording(): Boolean {
        if (!asrReady) {
            if (!asrInitializing) initializeAsr()
            return false
        }
        if (!hasMicrophonePermission()) {
            openPermissionActivity()
            return false
        }
        if (recorder.state !in setOf(AudioRecorderState.IDLE, AudioRecorderState.ERROR)) return false
        startRecording()
        return true
    }

    private fun onMicrophoneTouch(view: View, event: MotionEvent): Boolean {
        if (!view.isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                microphonePressed = true
                longPressTriggered = false
                if (recorder.state in setOf(AudioRecorderState.IDLE, AudioRecorderState.ERROR)) {
                    mainHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                microphonePressed = false
                mainHandler.removeCallbacks(longPressRunnable)
                if (longPressTriggered) {
                    if (recorder.state in setOf(AudioRecorderState.STARTING, AudioRecorderState.RECORDING)) {
                        Log.i(TAG, "MIC_GESTURE mode=hold action=stop")
                        recorder.stop()
                    }
                } else {
                    Log.i(TAG, "MIC_GESTURE mode=toggle action=click")
                    view.performClick()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                microphonePressed = false
                mainHandler.removeCallbacks(longPressRunnable)
                if (longPressTriggered) cancelRecording()
                return true
            }
        }
        return false
    }

    private fun onDeleteTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                deletePressed = true
                deleteRepeating = false
                view.isPressed = true
                mainHandler.postDelayed(deleteRepeatRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }

            MotionEvent.ACTION_UP -> {
                val wasRepeating = deleteRepeating
                stopDeleteRepeat()
                view.isPressed = false
                if (!wasRepeating) view.performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                stopDeleteRepeat()
                view.isPressed = false
                return true
            }
        }
        return false
    }

    private fun stopDeleteRepeat() {
        val wasRepeating = deleteRepeating
        deletePressed = false
        deleteRepeating = false
        mainHandler.removeCallbacks(deleteRepeatRunnable)
        if (wasRepeating) Log.d(TAG, "DELETE_KEY mode=repeat action=stop")
    }

    private fun deletePreviousCharacter() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    // Runs the field's own action (Send, Go, Search, Done) when it has one; otherwise inserts a new line.
    private fun sendEnterOrEditorAction() {
        val options = currentInputEditorInfo?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        val actionAvailable = action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
        if (actionAvailable && options and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun returnToPreviousInputMethod() {
        val previous = switchToPreviousInputMethod()
        if (!previous) {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        }
        Log.i(TAG, "IME_SWITCH_BACK previous=$previous pickerFallback=${!previous}")
    }

    private fun startRecording() {
        val engine = asrEngine ?: return
        val recognitionInputSession = inputSession
        val generation = ++recordingGeneration
        asrExecutor.execute { engine.reset() }
        capturedSamples = 0
        recognizedSegments = 0
        committedSegments = 0
        recordingSessionActive = true
        recognizeWhenStopped = true
        recordingCancelled = false
        recordingStartedAtMs = System.currentTimeMillis()
        Log.i(TAG, "RECORD_START sampleRate=$SAMPLE_RATE channel=mono encoding=pcm16")
        recorder.start { samples ->
            capturedSamples += samples.size
            executeAsr {
                try {
                    engine.acceptAudio(samples).forEach { text ->
                        postToMain { commitSegment(text, recognitionInputSession, generation) }
                    }
                } catch (error: Throwable) {
                    onAsrFailure(error, generation)
                }
            }
            val audioLevels = pcmAudioLevels(samples)
            postToMain {
                if (inputViewActive && recorder.state == AudioRecorderState.RECORDING && viewsInitialized()) {
                    voiceWaveView.setAudioLevels(audioLevels)
                }
            }
        }
    }

    private fun onRecorderStateChanged(state: AudioRecorderState) {
        if (!::statusView.isInitialized) return
        when (state) {
            AudioRecorderState.IDLE -> {
                if (recordingSessionActive) {
                    recordingSessionActive = false
                    val elapsedMs = (System.currentTimeMillis() - recordingStartedAtMs).coerceAtLeast(0L)
                    Log.i(TAG, "RECORD_STOP durationMs=$elapsedMs samples=$capturedSamples")
                    if (recognizeWhenStopped) {
                        recognizeWhenStopped = false
                        finishRecognition()
                    } else {
                        resetAsr()
                        if (recordingCancelled) {
                            recordingCancelled = false
                            renderCancelledState()
                        } else {
                            renderIdleState()
                        }
                    }
                } else {
                    renderIdleState()
                }
            }

            AudioRecorderState.STARTING -> {
                voiceState = VoiceState.STARTING
                showStatus(text(R.string.status_starting))
                setMicrophoneAppearance(recording = true, enabled = true)
            }

            AudioRecorderState.RECORDING -> {
                voiceState = VoiceState.RECORDING
                showStatus(text(R.string.status_listening), text(R.string.status_listening_hint))
                setMicrophoneAppearance(recording = true, enabled = true)
            }

            AudioRecorderState.STOPPING -> {
                voiceState = VoiceState.STOPPING
                showStatus(text(R.string.status_stopping))
                setMicrophoneAppearance(recording = true, enabled = false)
            }

            AudioRecorderState.ERROR -> Unit
        }
    }

    private fun renderIdleState() {
        if (!viewsInitialized()) return
        voiceState = VoiceState.IDLE
        when {
            asrInitializing -> {
                showStatus(
                    text(R.string.status_model_initializing),
                    text(R.string.status_model_initializing_hint),
                    busy = true,
                )
                setMicrophoneAppearance(recording = false, enabled = false)
            }

            asrInitError != null -> {
                voiceState = VoiceState.ERROR
                showStatus(
                    text(R.string.status_asr_init_failed),
                    text(R.string.status_asr_init_failed_hint),
                )
                setMicrophoneAppearance(recording = false, enabled = true)
            }

            !hasMicrophonePermission() -> {
                showStatus(text(R.string.status_permission_required), text(R.string.status_permission_required_hint))
                setMicrophoneAppearance(recording = false, enabled = true)
            }

            else -> {
                showStatus(text(R.string.status_ready), text(R.string.status_ready_hint))
                setMicrophoneAppearance(recording = false, enabled = true)
            }
        }
    }

    private fun showRecorderError(error: Throwable) {
        recordingSessionActive = false
        recognizeWhenStopped = false
        recordingCancelled = false
        recordingGeneration++
        resetAsr()
        Log.e(TAG, "RECORD_FAILED", error)
        if (::statusView.isInitialized) {
            voiceState = VoiceState.ERROR
            showStatus(
                text(R.string.status_recording_failed),
                text(R.string.status_recording_failed_hint, error.message ?: error.javaClass.simpleName),
            )
            setMicrophoneAppearance(recording = false, enabled = true)
        }
    }

    private fun cancelRecording() {
        if (!voiceState.canCancelRecording) return
        recognizeWhenStopped = false
        recordingCancelled = true
        recordingGeneration++
        Log.i(TAG, "RECORD_CANCEL")
        recorder.stop()
    }

    private fun renderCancelledState() {
        if (!viewsInitialized()) return
        voiceState = VoiceState.IDLE
        showStatus(text(R.string.status_cancelled))
        setMicrophoneAppearance(recording = false, enabled = true)
    }

    private fun reloadAsrIfSettingsChanged() {
        val settings = AppSettings(this).load()
        if (settings != loadedAsrSettings) initializeAsr(settings)
    }

    private fun initializeAsr(settings: AsrSettings = AppSettings(this).load()) {
        val generation = ++asrInitializationGeneration
        loadedAsrSettings = settings
        asrInitializing = true
        asrReady = false
        asrInitError = null
        if (::statusView.isInitialized) renderIdleState()
        asrExecutor.execute {
            try {
                val modelManager = ModelManager(this)
                val engine = SenseVoiceEngine(modelManager.installFromAssets(), settings)
                engine.init()
                if (destroyed || generation != asrInitializationGeneration) {
                    engine.release()
                    return@execute
                }
                val previousEngine = asrEngine
                asrEngine = engine
                previousEngine?.release()
                postToMain {
                    if (generation != asrInitializationGeneration) return@postToMain
                    asrInitializing = false
                    asrReady = true
                    renderIdleState()
                }
            } catch (error: Throwable) {
                Log.e(TAG, "MODEL_INIT_FAILED", error)
                postToMain {
                    if (generation != asrInitializationGeneration) return@postToMain
                    asrInitializing = false
                    asrReady = false
                    asrInitError = error.message ?: error.javaClass.simpleName
                    renderIdleState()
                }
            }
        }
    }

    private fun finishRecognition() {
        val engine = asrEngine
        if (engine == null) {
            voiceState = VoiceState.ERROR
            showStatus(text(R.string.status_asr_unavailable), text(R.string.status_asr_unavailable_hint))
            setMicrophoneAppearance(recording = false, enabled = true)
            Log.e(TAG, "ASR_FAILED reason=engine_unavailable")
            return
        }
        voiceState = VoiceState.PROCESSING
        showStatus(text(R.string.status_processing), busy = true)
        setMicrophoneAppearance(recording = false, enabled = false)
        val recognitionInputSession = inputSession
        val generation = recordingGeneration
        asrExecutor.execute {
            try {
                val results = engine.finish()
                postToMain {
                    if (generation != recordingGeneration) return@postToMain
                    results.forEach { text -> commitSegment(text, recognitionInputSession, generation) }
                    if (recognizedSegments == 0) {
                        voiceState = VoiceState.RESULT
                        showStatus(text(R.string.status_empty_result), text(R.string.status_empty_result_hint))
                    } else if (committedSegments == recognizedSegments) {
                        renderIdleState()
                    }
                    setMicrophoneAppearance(recording = false, enabled = true)
                }
            } catch (error: Throwable) {
                onAsrFailure(error, generation)
            }
        }
    }

    private fun commitSegment(text: String, recognitionInputSession: Long, generation: Long) {
        if (generation != recordingGeneration) {
            Log.w(TAG, "COMMIT_FAILED reason=recording_changed textLength=${text.length}")
            return
        }
        recognizedSegments++
        if (!inputViewActive || recognitionInputSession != inputSession) {
            Log.w(TAG, "COMMIT_FAILED reason=input_session_changed textLength=${text.length}")
            showStatus(text(R.string.status_result_not_committed), text(R.string.status_result_not_committed_hint))
            return
        }

        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            Log.w(TAG, "COMMIT_FAILED reason=no_input_connection textLength=${text.length}")
            showStatus(text(R.string.status_result_not_committed), text(R.string.status_result_not_committed_hint))
            return
        }

        val committed = runCatching { inputConnection.commitText(text, 1) }
            .onFailure { Log.e(TAG, "COMMIT_FAILED reason=exception textLength=${text.length}", it) }
            .getOrDefault(false)
        if (committed) {
            committedSegments++
            Log.i(TAG, "COMMIT_SUCCESS textLength=${text.length}")
        } else {
            Log.w(TAG, "COMMIT_FAILED reason=rejected textLength=${text.length}")
            showStatus(text(R.string.status_result_not_committed), text(R.string.status_result_not_committed_hint))
        }
    }

    private fun onAsrFailure(error: Throwable, generation: Long) {
        Log.e(TAG, "ASR_FAILED", error)
        postToMain {
            if (generation != recordingGeneration) return@postToMain
            recognizeWhenStopped = false
            recordingGeneration++
            recorder.stop()
            resetAsr()
            voiceState = VoiceState.ERROR
            showStatus(
                text(R.string.status_asr_failed),
                text(R.string.status_asr_failed_hint, error.message ?: error.javaClass.simpleName),
            )
            setMicrophoneAppearance(recording = false, enabled = true)
        }
    }

    private fun resetAsr() {
        val engine = asrEngine ?: return
        executeAsr { engine.reset() }
    }

    private fun executeAsr(action: () -> Unit) {
        if (destroyed) return
        try {
            asrExecutor.execute(action)
        } catch (error: RejectedExecutionException) {
            if (!destroyed) throw error
        }
    }

    private fun postToMain(action: () -> Unit) {
        if (!destroyed) mainHandler.post { if (!destroyed) action() }
    }

    private fun viewsInitialized(): Boolean =
        ::statusView.isInitialized && ::voiceWaveView.isInitialized && ::microphoneButton.isInitialized

    private fun text(id: Int, vararg args: Any): String = uiContext().getString(id, *args)

    // Title is the state, hint is the next action. Busy states show a gray sheen in the detail slot instead.
    // Both views stay laid out (INVISIBLE, not GONE) so the panel height never changes.
    private fun showStatus(title: String, hint: String? = null, busy: Boolean = false) {
        if (!viewsInitialized()) return
        statusView.text = title
        hintView.text = hint.orEmpty()
        hintView.visibility = if (busy) View.INVISIBLE else View.VISIBLE
        loadingSkeleton.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) loadingSkeleton.post { if (loadingSkeleton.visibility == View.VISIBLE) startSheen() } else stopSheen()
    }

    private fun startSheen() {
        val travel = loadingSkeleton.width.toFloat()
        if (travel == 0f || sheenAnimator?.isRunning == true) return
        sheenAnimator = ObjectAnimator.ofFloat(loadingSheen, View.TRANSLATION_X, -loadingSheen.width.toFloat(), travel).apply {
            duration = SHEEN_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopSheen() {
        sheenAnimator?.cancel()
        sheenAnimator = null
        if (::loadingSheen.isInitialized) loadingSheen.translationX = 0f
    }

    private fun setMicrophoneAppearance(recording: Boolean, enabled: Boolean) {
        if (!::microphoneButton.isInitialized || !::voiceWaveView.isInitialized) return
        microphoneButton.isEnabled = enabled
        microphoneButton.setBackgroundResource(
            when {
                recording -> R.drawable.microphone_background_recording
                !enabled -> R.drawable.microphone_background_disabled
                else -> R.drawable.microphone_background_idle
            },
        )
        microphoneButton.setImageResource(if (recording) R.drawable.ic_stop else R.drawable.ic_microphone)
        microphoneButton.imageTintList = ColorStateList.valueOf(
            getColor(
                if (!enabled && !recording) {
                    R.color.voice_mic_disabled_content
                } else {
                    R.color.voice_on_accent
                },
            ),
        )
        voiceWaveView.setWaveColor(
            getColor(
                when {
                    recording -> R.color.voice_wave_recording
                    !enabled -> R.color.voice_wave_disabled
                    else -> R.color.voice_wave_idle
                },
            ),
        )
        voiceWaveView.setRecording(voiceState == VoiceState.RECORDING)
        microphoneButton.contentDescription = uiContext().getString(
            when {
                !enabled -> R.string.microphone_busy
                recording -> R.string.stop_recording
                else -> R.string.start_recording
            },
        )
    }

    private fun hasMicrophonePermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun openPermissionActivity() {
        startActivity(Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(SettingsActivity.EXTRA_REQUEST_MICROPHONE_PERMISSION, true)
        })
    }

    private companion object {
        const val TAG = "FCITX5-SENSEVOICE"
        const val SAMPLE_RATE = 16_000
        const val DELETE_REPEAT_INTERVAL_MS = 100L
        const val SHEEN_DURATION_MS = 1400L
    }
}
