package com.pawxy.youtubetomp3.viewModel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.FFmpeg
import com.pawxy.youtubetomp3.model.Video
import com.pawxy.youtubetomp3.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
        object Saving : Event()
        object Empty: Event()
        object Converting: Event()
    }


    private val _state = MutableStateFlow<Event>(Event.Empty)
    val state: StateFlow<Event> = _state

    val progressionState = MutableLiveData<String>()
    val progression = MutableLiveData<String>()
    val progressBar = MutableLiveData<Int>()

    fun startDownload(){
        _state.value=Event.StartDownload
        progressionState.value="Downloading..."
        progressBar.value=0
    }
    private fun converting()
    {
        if (_state.value is Event.StartDownload)
        {
            _state.value=Event.Converting

        }
    }
    private fun saving()
    {
        if (_state.value is Event.Converting)
        {
            _state.value=Event.Saving
        }
    }
    private fun onSuccess(msg:String)
    {
        if (_state.value is Event.Saving)
        {
            _state.value=Event.Success(msg)
        }
    }

    private fun clearTemp(directoryPath: String,title: String)
    {
        val file = File("$directoryPath/$title.m4a")
        if (file.exists()) {
            val deleted = file.delete()
            if (deleted) {
                Log.i("delete temp","Success")
            } else {
                Log.i("delete temp","Failed")
            }
        } else {
            Log.i("delete temp","File not exist")
        }
    }
    fun restart(){
        _state.value= Event.Empty
    }
    private fun onFailure(error:String)
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
            val video = async {
                NetworkHelper.doNetworkCall(url)
            }.await()
            video?.let {
                async {
                    saveToFile(it, directoryPath,title)
                }.await()

                val outputPath=getUniqueFileName("$directoryPath/$title.mp3")
                withContext(Dispatchers.Main)
                {
                    converting()
                }

                async(Dispatchers.IO) {
                    convertM4AToMP3("$directoryPath/$title.m4a", outputPath,directoryPath)
                }.await()

                withContext(Dispatchers.Main)
                {
                    if (_state.value is Event.Saving)
                    {
                        onSuccess("MP3 successfully saved into selected folder")

                    }
                    clearTemp(directoryPath,title)
                }
            }
        }

    }
    private fun getUniqueFileName(filePath: String): String {
        var file = File(filePath)
        if (!file.exists()) {
            // File doesn't exist, return the original file name
            return filePath
        }

        val parentDir = file.parent
        val originalName = file.nameWithoutExtension
        val extension = file.extension
        var counter = 1

        var uniqueFileName = "$originalName.$extension"

        // Generate a new unique file name
        while (file.exists()) {
            uniqueFileName = "$originalName ($counter).$extension"
            file = File(parentDir, uniqueFileName)
            counter++
        }

        return file.absolutePath
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
    private fun convertM4AToMP3(inputPath: String, outputPath: String, directoryPath: String) {
        viewModelScope.launch {
            val success = async {
                convertM4AToMP3Internal(inputPath, outputPath)
            }.await()
            if (success) {
                saving()
            } else {
                onFailure("Failed to convert")
            }
        }
    }
    private fun convertM4AToMP3Internal(inputPath: String, outputPath: String): Boolean {
        return try {
            val command = arrayOf(
                "-i", inputPath,
                "-dn",
                "-ignore_unknown",
                "-sn",
                "-c:a", "libmp3lame",
                "-q:a", "2",
                outputPath
            )

            val result = FFmpeg.execute(command)
            result == RETURN_CODE_SUCCESS
        } catch (e: Exception) {
            onFailure("Failed to convert")
            e.printStackTrace()
            false
        }
    }

    //Save file to where user chosen
    private suspend fun saveToFile(video: Video, directoryPath: String,fileName: String) {
            var temp:OutputStream? = null
            var totalByteRead:Long =0

            try {
                val directory = File(directoryPath)
                val tempFile= File(directory, "$fileName.m4a")
                temp = withContext(Dispatchers.IO) {
                    FileOutputStream(tempFile)
                }
                val buffer = ByteArray(64 * 1024) // 64kb buffer
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
                        val progress=totalByteRead*100/video.contentLength/3
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
                    onFailure("Please check your internet connection ")
                }

            } finally {
                withContext(Dispatchers.IO) {
                    temp?.close()
                }
                withContext(Dispatchers.IO) {
                    video.inputStream.close()
                }

            }
    }
}