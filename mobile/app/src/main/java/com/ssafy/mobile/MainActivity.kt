package com.ssafy.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ssafy.mobile.core.ui.theme.MobileTheme
import com.ssafy.mobile.feature.sample.presentation.SampleRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobileTheme {
                SampleRoute()
            }
        }
    }
}
