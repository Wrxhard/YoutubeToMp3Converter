package com.pawxy.youtubetomp3.screen

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.pawxy.youtubetomp3.R
import com.pawxy.youtubetomp3.viewModel.SimpleViewModel
import com.pawxy.youtubetomp3.databinding.DownloadscreenBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadScreen : AppCompatActivity() {
    private lateinit var binding: DownloadscreenBinding
    private lateinit var mViewModel: SimpleViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this,R.layout.downloadscreen)
        mViewModel= ViewModelProvider(this)[SimpleViewModel::class.java]
        binding.mViewModel= mViewModel
        binding.lifecycleOwner=this
        checkState()
        setUp()
        mViewModel.startDownload()

    }
    private fun setUp()
    {
        intent.apply {
            val thumbnail = getStringExtra("thumbnail")
            val title = getStringExtra("title")
            val viewCount = getStringExtra("view_count")
            val likeCount = getStringExtra("like_count")

            Glide.with(this@DownloadScreen)
                .load(thumbnail)
                .placeholder(R.drawable.placeholder)
                .into(binding.imageView)

            binding.title.text = title
            binding.likeCount.text = "  $likeCount"
            binding.viewCount.text = "  $viewCount"
            binding.downloadProgress.progressDrawable=ContextCompat.getDrawable(this@DownloadScreen,R.drawable.progress_bar_download)


            mViewModel.progressBar.observe(this@DownloadScreen){
                binding.downloadProgress.setProgress(it,true)
            }

            binding.DownloadAgain.setOnClickListener {
                mViewModel.restart()
            }
        }

    }
    private fun checkState(){
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED)
            {
                mViewModel.state.collectLatest { event ->
                    when(event){
                        is SimpleViewModel.Event.Empty -> {
                            finish()
                        }
                        is SimpleViewModel.Event.StartDownload -> {
                            val streamLink = intent.getStringExtra("stream_link")
                            val directory = intent.getStringExtra("directory")
                            if (streamLink!= null  && directory!=null)
                            {
                                mViewModel.getVideo(streamLink,directory,binding.title.text.toString())
                            }
                        }
                        is SimpleViewModel.Event.Converting ->
                        {
                            binding.currentState.text="Converting..."
                            binding.stateProgress.text=""
                            binding.downloadProgress.progressDrawable=ContextCompat.getDrawable(this@DownloadScreen,R.drawable.progress_bar_convert)
                            binding.downloadProgress.setProgress(50,true)

                        }
                        is SimpleViewModel.Event.Saving ->
                        {
                            binding.currentState.text="Saving..."
                            binding.stateProgress.text=""
                            binding.downloadProgress.progressDrawable=ContextCompat.getDrawable(this@DownloadScreen,R.drawable.progress_bar_convert)
                            binding.downloadProgress.setProgress(80,true)
                        }
                        is SimpleViewModel.Event.Success ->
                        {
                            withContext(Dispatchers.Main)
                            {
                                binding.currentState.text="Success"
                                binding.stateProgress.text=""
                                val drawable1 = ContextCompat.getDrawable(this@DownloadScreen, R.drawable.success)
                                binding.stateProgress.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable1, null, null, null)

                                binding.downloadProgress.progressDrawable=ContextCompat.getDrawable(this@DownloadScreen,R.drawable.progress_bar_success)
                                binding.downloadDescription.visibility= View.VISIBLE
                                binding.downloadProgress.setProgress(100,true)
                                binding.downloadDescription.text= " "+event.result
                                val drawable2 = ContextCompat.getDrawable(this@DownloadScreen, R.drawable.success_icon)
                                binding.downloadDescription.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable2, null, null, null)

                                binding.DownloadAgain.visibility=View.VISIBLE



                            }
                        }
                        is SimpleViewModel.Event.Failure ->
                        {
                            withContext(Dispatchers.Main)
                            {
                                if (binding.currentState.text!="Failed")
                                {
                                    binding.currentState.text="Failed"
                                    binding.downloadProgress.setProgress(100,true)
                                    binding.stateProgress.text=""
                                    val drawable1 = ContextCompat.getDrawable(this@DownloadScreen, R.drawable.failed)
                                    binding.stateProgress.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable1, null, null, null)

                                    binding.downloadProgress.progressDrawable=ContextCompat.getDrawable(this@DownloadScreen,R.drawable.progress_bar_failed)
                                    binding.downloadDescription.visibility= View.VISIBLE
                                    binding.downloadDescription.text= " "+event.error
                                    val drawable = ContextCompat.getDrawable(this@DownloadScreen, R.drawable.fail_icon)
                                    binding.downloadDescription.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)

                                    binding.DownloadAgain.visibility=View.VISIBLE
                                }

                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}