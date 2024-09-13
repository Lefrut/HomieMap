package com.world.homiemap

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.world.homiemap.presentation.MapFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        handleInsets()
        navigateToMapFragment()
    }

    private fun handleInsets() {
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun navigateToMapFragment(){
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            add<MapFragment>(R.id.fragment_container_view)
        }
    }
}