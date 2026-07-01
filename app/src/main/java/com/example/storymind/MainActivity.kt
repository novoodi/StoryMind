package com.example.storymind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.storymind.ui.StoryMindApp
import com.example.storymind.ui.theme.StoryMindTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StoryMindTheme {
                StoryMindApp(modifier =
                    Modifier.fillMaxSize())
            }
        }
    }
}