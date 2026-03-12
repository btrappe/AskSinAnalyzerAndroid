package com.asksin.analyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.asksin.analyzer.ui.screens.AppNavigation
import com.asksin.analyzer.ui.theme.AskSinTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AskSinTheme {
                AppNavigation(viewModel)
            }
        }
    }
}
