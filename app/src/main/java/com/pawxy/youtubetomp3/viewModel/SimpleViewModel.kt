package com.pawxy.youtubetomp3.viewModel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pawxy.youtubetomp3.model.Video
import com.pawxy.youtubetomp3.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream


class SimpleViewModel:ViewModel() {
    sealed class Event {
        class Success(val result: String): Event()
        class Failure(val error: String): Event()
        object GrabbingData : Event()
        object StartDownload : Event()
        object Downloading : Event()
        object Saving : Event()
        object Empty: Event()
    }


    private val _state = MutableStateFlow<Event>(Event.Empty)
    val state: StateFlow<Event> = _state

    val progressionState = MutableLiveData<String>()
    val progression = MutableLiveData<String>()
    val progressBar = MutableLiveData<Int>()

    init {
        progressionState.value="Downloading..."
        progressBar.value=0
    }

    fun startDownload(){
        _state.value=Event.StartDownload
    }
    fun restart(){
        _state.value= Event.Empty
    }
    fun onFailure(error:String)
    {
        _state.value= Event.Failure(error)
    }
    fun startGrabbing()
    {
        _state.value= Event.GrabbingData
    }

    //get the audio file from url (yt-dlp)
    fun getVideo(url:String,directoryPath: String,title:String)
    {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main)
            {
                progressionState.value = "Downloading..."
                _state.value= Event.Downloading
            }
            val videoInfo = NetworkHelper.doNetworkCall(url)
            Log.i("video","$videoInfo")
            Log.i("title", title)
            if (videoInfo!=null)
            {
                saveToFile(videoInfo, directoryPath, title)
            }
        }
    }

    fun formatFileSize(size: Int): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var fileSize = size.toDouble()
        var unitIndex = 0

        while (fileSize >= 1024 && unitIndex < units.size - 1) {
            fileSize /= 1024
            unitIndex++
        }

        return "%.2f".format(fileSize) + units[unitIndex]
    }

    //Save file to where user chosen
    private suspend fun saveToFile(video: Video, directoryPath: String, fileName: String) {
            var temp:OutputStream? = null
            var totalByteRead:Long =0

            try {
                val directory = File(directoryPath)
                val tempFile= File(directory, "$fileName.m4a")
                temp = withContext(Dispatchers.IO) {
                    FileOutputStream(tempFile)
                }
                val buffer = ByteArray(4 * 1024) // 4KB buffer
                var bytesRead: Int
                while (withContext(Dispatchers.IO) {
                        video.inputStream.read(buffer)
                    }.also { bytesRead = it } != -1) {

                    withContext(Dispatchers.IO) {
                        temp.write(buffer, 0, bytesRead)
                    }

                    totalByteRead += bytesRead

                    withContext(Dispatchers.Main)
                    {
                        progression.value="${formatFileSize(totalByteRead.toInt())} / ${formatFileSize(video.contentLength.toInt())}"
                        val progress=totalByteRead/video.contentLength*100
                        progressBar.value=progress.toInt()
                    }
                }
                withContext(Dispatchers.IO) {
                    temp.flush()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main)
                {
                    progressionState.value= "Failed"
                    _state.value= Event.Failure("Please check your internet connection")
                }

            } finally {
                withContext(Dispatchers.IO) {
                    temp?.close()
                }
                withContext(Dispatchers.IO) {
                    video.inputStream.close()
                }
                withContext(Dispatchers.Main)
                {
                    progressionState.value= "Success"
                    _state.value= Event.Success("M4A successfully saved into selected folder")
                }

            }
    }
}