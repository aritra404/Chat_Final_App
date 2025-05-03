package aritra.seal.new_chat.Story

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class StoriesProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val progressBars = mutableListOf<ProgressBar>()
    private var storiesCount = 0
    private var listener: StoriesListener? = null
    private var currentStoryIndex = 0
    private var storyDuration = 5000L // Default 5 seconds per story
    private var job: Job? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun setStoriesCount(count: Int) {
        storiesCount = count
        removeAllViews()
        progressBars.clear()

        // Create progress bars for each story
        for (i in 0 until count) {
            val progressBar = ProgressBar(
                context,
                null,
                android.R.attr.progressBarStyleHorizontal
            )

            val params = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            params.marginStart = 5
            params.marginEnd = 5

            progressBar.layoutParams = params
            progressBar.max = 100
            progressBar.progress = 0

            // Set a visible background color
            progressBar.progressDrawable = ContextCompat.getDrawable(
                context,
                android.R.drawable.progress_horizontal
            )

            addView(progressBar)
            progressBars.add(progressBar)
        }
    }

    fun setStoriesListener(listener: StoriesListener) {
        this.listener = listener
    }

    fun startStories(startIndex: Int = 0) {
        currentStoryIndex = startIndex
        startStoryProgress()
    }

    private fun startStoryProgress() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            while (currentStoryIndex < storiesCount) {
                // Reset previous progress bar
                if (currentStoryIndex > 0) {
                    progressBars[currentStoryIndex - 1].progress = 100
                }

                // Animate current progress bar
                for (progress in 0..100) {
                    progressBars[currentStoryIndex].progress = progress
                    delay(storyDuration / 100)
                }

                // Move to next story
                listener?.onNext()
                currentStoryIndex++
            }

            // All stories completed
            listener?.onComplete()
        }
    }

    fun pause() {
        job?.cancel()
    }

    fun resume() {
        startStoryProgress()
    }

    interface StoriesListener {
        fun onNext()
        fun onPrev()
        fun onComplete()
    }
}