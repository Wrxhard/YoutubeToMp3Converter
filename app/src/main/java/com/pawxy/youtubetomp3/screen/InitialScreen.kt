package com.pawxy.youtubetomp3.screen

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.pawxy.youtubetomp3.model.VideoOverview
import com.pawxy.youtubetomp3.viewModel.SimpleViewModel
import com.pawxy.youtubetomp3.databinding.InitialscreenBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.json.JSONObject


class InitialScreen : AppCompatActivity() {
    private lateinit var binding: InitialscreenBinding
    private lateinit var activityLauncher: ActivityResultLauncher<Intent>
    private lateinit var mViewModel: SimpleViewModel


    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = InitialscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mViewModel=ViewModelProvider(this)[SimpleViewModel::class.java]

        //Call when we need to implement python code
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        //register activity to open folder tree
        activityLauncher= registerForActivityResult(ActivityResultContracts.StartActivityForResult()
        ) {
            if(it.resultCode == Activity.RESULT_OK)
            {
                val intent= it.data
                val uri = intent?.data
                uri?.let {
                    getDirectoryPathFromUri(uri)?.let {
                            path -> Log.i("directory", path)
                            binding.LinkToFolder.setText(path)
                    }
                }
            }
        }
        //Check event state
        checkState()
        //Default directory is Download
        binding.LinkToFolder.setText(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
        binding.DownloadButton.setOnClickListener {
            val videoUrl = binding.LinkToVideo.text.toString()

            if (binding.LinkToVideo.isEnabled)
            {
                try {
                    if (videoUrl.contains("https://youtu"))
                    {
                        mViewModel.startGrabbing()
                        lifecycleScope.launch(Dispatchers.IO)
                        {
                            repeatOnLifecycle(Lifecycle.State.CREATED)
                            {
                                //Get Python Instance
                                val py = Python.getInstance()
                                //Call YoutubeVideoDownload.py
                                val youtubeHelper = py.getModule("YoutubeVideoDownload")
                                //Call StringHelper.py
                                val stringHelper = py.getModule("StringHelper")
                                //Call get_video function from python (yt-dlp)
                                val raw = youtubeHelper.callAttr("get_video_info", videoUrl)
                                val jsonObject=JSONObject(raw.toString())
                                //Grab the first url that store video
                                val videoOverview=filerJson(jsonObject).copy()
                                //Remove all special character,emoji,icon and non english alphabet
                                val title = stringHelper.callAttr("remove_special_characters",videoOverview.title).toString()
                                withContext(Dispatchers.Main)
                                {
                                    val intent = Intent(this@InitialScreen,DownloadScreen::class.java).apply {
                                        videoOverview.let {
                                            putExtra("title",title)
                                            putExtra("thumbnail",videoOverview.thumbnail)
                                            putExtra("stream_link",videoOverview.streamLink)
                                            putExtra("view_count",videoOverview.view)
                                            putExtra("like_count",videoOverview.like)
                                            putExtra("directory",binding.LinkToFolder.text.toString())
                                        }

                                    }
                                    startActivity(intent)
                                }
                            }
                        }


                    }else{
                        if (binding.LinkToVideo.isEnabled)
                        {
                            mViewModel.restart()
                            Toast.makeText(this@InitialScreen,"Please Provide A Link To Video",Toast.LENGTH_SHORT).show()
                        }

                    }
                } catch (e: Exception) {
                    Toast.makeText(this,"Failed to grab video info",Toast.LENGTH_SHORT).show()
                    mViewModel.restart()
                }
            }


        }
        binding.LinkToFolder.setOnClickListener {
            openFilePicker()
        }
    }

    override fun onRestart() {
        super.onRestart()
        enableUserInput()
    }

    private fun openFilePicker()
    {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        activityLauncher.launch(intent)
    }
    //Check Event State
    private fun checkState()
    {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED)
            {
                mViewModel.state.collectLatest {event ->
                    when(event)
                    {
                        is SimpleViewModel.Event.Empty
                        -> enableUserInput()

                        is SimpleViewModel.Event.GrabbingData
                        -> disableUserInput()

                        else
                        -> Unit
                    }
                }
            }
        }
    }
    private fun disableUserInput()
    {
        binding.btnText.text=" Grabbing Info..."
        binding.guideline.setGuidelinePercent(0.365F)
        binding.progressBar.visibility=View.VISIBLE
        binding.DownloadButton.setBackgroundColor(Color.parseColor("#9D7CC8"))
        binding.LinkToVideo.isEnabled=false
        binding.LinkToFolder.isEnabled=false
        binding.LinkInputField.alpha=0.5f
        binding.StoreDirectory.alpha=0.5f
    }
    private fun enableUserInput()
    {
        binding.btnText.text="Download"
        binding.guideline.setGuidelinePercent(0.4F)
        binding.progressBar.visibility=View.GONE
        binding.DownloadButton.setBackgroundColor(Color.parseColor("#892EFF"))
        binding.LinkToVideo.isEnabled=true
        binding.LinkToFolder.isEnabled=true
        binding.LinkInputField.alpha=1f
        binding.StoreDirectory.alpha=1f
    }


    private fun getDirectoryPathFromUri(uri: Uri?): String? {
        if (uri == null) return null

        val documentFile = DocumentFile.fromTreeUri(this, uri)

        if (documentFile != null && documentFile.isDirectory) {
            val documentUri = documentFile.uri

            // Check if the documentUri is a tree uri
            if (DocumentsContract.isTreeUri(documentUri)) {
                val documentId = DocumentsContract.getTreeDocumentId(documentUri)

                if (documentId != null) {
                    val split = documentId.split(":")
                    val storageId = split[0]

                    // Build the directory path
                    val directoryPath = "${Environment.getExternalStorageDirectory()}/$storageId/${split[1]}"
                    return directoryPath.replace("/primary","")
                }
            }
        }

        return null
    }

    //Grab the first url that store video
    private fun filerJson(data:JSONObject): VideoOverview {
        val title = data.getString("title")
        val view = data.getString("view_count")
        val like = data.getString("like_count")
        val formats = data.getJSONArray("formats")
        val thumbnail = data.getJSONArray("thumbnails").getJSONObject(0).getString("url")
        thumbnail.replace("""\/""".toRegex(),"/")

        for (i in 0 until formats.length())
        {
            val streamLink=formats.getJSONObject(i).getString("url")
            if(streamLink.contains("googlevideo"))
            {
                Log.i("steam",streamLink.takeLast(10))
                return VideoOverview(title,thumbnail,streamLink,view, like)
            }
        }
        return VideoOverview(title,thumbnail,"",view, like)

    }
}