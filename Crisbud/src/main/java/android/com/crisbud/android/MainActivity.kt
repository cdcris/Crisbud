package android.com.crisbud.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.com.crisbud.CrisbudApp
import android.com.crisbud.db.DatabaseContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DatabaseContext.application = applicationContext
        setContent {
            CrisbudApp()
        }
    }
}
