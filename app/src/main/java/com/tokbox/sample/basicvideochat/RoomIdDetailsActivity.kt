package com.tokbox.sample.basicvideochat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class RoomIdDetailsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_id_details)


        val button = findViewById<Button>(R.id.start_meeting)
        val edtText = findViewById<EditText>(R.id.meeting_id)
        edtText.setText(getRandomNumber().toString())

        button.setOnClickListener {
            val text = edtText.text.toString()
            if (!text.isNullOrEmpty()) {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("roomId", text)
                startActivity(intent)
                finish()
            }
        }


    }

    private fun getRandomNumber(): Int {
        val rand = Random()
        val maxNumber = 1000
        return rand.nextInt(maxNumber) + 1
    }
}