package com.example.swaggerwhy
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader

class PictureActivity : AppCompatActivity() {
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var handler: Handler
    private lateinit var textView: TextView
    private lateinit var backBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_picture)

        val bitmap = MainActivity.sBitmap
        val imageView = findViewById<ImageView>(R.id.captured_view)
        imageView.setImageBitmap(bitmap)

        backBtn = findViewById<Button>(R.id.back_button)
        backBtn.setOnClickListener {
            finish()
        }

        uploadImage(bitmap!!)

        mediaPlayer = MainActivity.sMediaPlaer!!
        handler = Handler(Looper.getMainLooper())
        textView = findViewById<TextView>(R.id.text)
        handler.postDelayed(Runnable {
            backBtn.visibility = Button.VISIBLE
        }, 4000)
    }

    private fun uploadImage(bitmap: Bitmap) {
        val client = OkHttpClient()
        val url = "http://110.40.175.218:5000/gen"

        val outStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream);

        val requestBody = outStream.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("graph", "graph", requestBody)
        val request = Request.Builder()
            .url(url)
            .post(
                MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(part)
                .build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Photo Upload fail: ${e.toString()}")
            }

            override fun onResponse(call: Call, response: Response) {
                var name = response.body?.string()
                Log.i(TAG, "Photo Upload successful: $name")

                if (name == null)
                    return

                name = name.split("_")[0]
                val metadata = getMetaData(name.toString()) ?: return
                flushKnowledge(metadata)
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun flushKnowledge(metadata: JsonObject) {
        handler.post(Runnable {
            val name = metadata.get("name").asString
            Toast.makeText(baseContext, name, Toast.LENGTH_SHORT).show()

            val namePost = if (isZh()) "_cn" else "_en"

            val audioName = metadata.get("audio").asString
            mediaPlayer.setOnCompletionListener {
                val question = metadata.get("question$namePost").asString
                val answer = metadata.get("answer$namePost").asString
                val textContent = "Q: $question\n\nA: $answer"
                var scrollPosition =0
                val runnable = object : Runnable {
                    override fun run() {
                        scrollPosition += 15
                        textView.scrollTo(0, scrollPosition)
                        handler.postDelayed(this, 1000)
                    }
                }

                textView.text = textContent
                if (textContent.length >= 400) {
                    handler.post(runnable)
                }

                mediaPlayer.setOnCompletionListener{
                    try {
                        handler.removeCallbacks(runnable)
                    } catch (_: Exception) {
                    }
                    backBtn.performClick()
                }
                playAudio(audioName + "_answer")
            }
            playAudio(audioName + "_question")

            val question = metadata.get("question$namePost").asString
            textView.text = "Q: $question"
        })
    }

    private fun isZh(): Boolean {
        val locale = baseContext.resources.configuration.locale
        val language = locale.language;
        return language.endsWith("zh")
    }

    private fun getMetaData(name: String): JsonObject? {
        val inputStream = baseContext.resources.openRawResource(R.raw.metadata)
        val reader = BufferedReader(InputStreamReader(inputStream))

        reader.useLines { lines ->
            var metadata : JsonObject? = null
            for (line in lines) {
                metadata = Gson().fromJson(line, JsonObject::class.java)
                if (name == metadata.get("name").asString) {
                    return metadata
                }
            }
            return metadata
        }
    }

    private fun playAudio(audioName: String) {
        val resourceId = applicationContext.resources.getIdentifier(audioName, "raw", baseContext.packageName)
        val assetFileDescriptor = applicationContext.resources.openRawResourceFd(resourceId)
        mediaPlayer.reset()
        mediaPlayer.setDataSource(assetFileDescriptor)
        mediaPlayer.prepare()
        mediaPlayer.start()
        assetFileDescriptor.close()
    }

    companion object {
        private const val TAG = "PictureActivity"
    }
}