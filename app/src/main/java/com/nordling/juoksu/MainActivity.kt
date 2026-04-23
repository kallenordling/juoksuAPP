package com.nordling.juoksu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nordling.juoksu.databinding.ActivityMainBinding
import com.nordling.juoksu.db.AppDatabase

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val db by lazy { AppDatabase.getInstance(this) }

    private val adapter = SessionAdapter(
        onOpen = { s ->
            startActivity(Intent(this, SessionDetailActivity::class.java).apply {
                putExtra(SessionDetailActivity.EXTRA_SESSION_ID, s.id)
            })
        },
        onMap = { s ->
            startActivity(Intent(this, MapActivity::class.java).apply {
                putExtra(MapActivity.EXTRA_SESSION_ID, s.id)
            })
        }
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startRun()
        } else {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        binding.recyclerSessions.adapter = adapter
        db.sessionDao().getAll().observe(this) { sessions ->
            adapter.submitList(sessions)
            binding.textEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.btnStartRun.setOnClickListener { checkPermissionsAndStart() }
    }

    private fun checkPermissionsAndStart() {
        val required = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startRun() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startRun() {
        startActivity(Intent(this, RunActivity::class.java))
    }
}
