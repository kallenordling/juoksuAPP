package com.nordling.juoksu

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nordling.juoksu.databinding.ActivityGoalBinding

class GoalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGoalBinding
    private var selectedMeters: Double = GoalPresets.ALL.first().meters

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGoalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Goal"

        val names = GoalPresets.ALL.map { it.name }
        binding.spinnerDistance.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, names
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.spinnerDistance.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                selectedMeters = GoalPresets.ALL[pos].meters
                refreshPacePreview()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        val existing = GoalStore.get(this)
        if (existing != null) {
            val idx = GoalPresets.ALL.indexOfFirst {
                kotlin.math.abs(it.meters - existing.distanceMeters) < 0.5
            }.coerceAtLeast(0)
            binding.spinnerDistance.setSelection(idx)
            selectedMeters = GoalPresets.ALL[idx].meters
            binding.editHours.setText((existing.targetSeconds / 3600).toString())
            binding.editMinutes.setText(((existing.targetSeconds % 3600) / 60).toString())
            binding.editSeconds.setText((existing.targetSeconds % 60).toString())
        }

        binding.editHours.addTextChangedListener(simpleWatcher { refreshPacePreview() })
        binding.editMinutes.addTextChangedListener(simpleWatcher { refreshPacePreview() })
        binding.editSeconds.addTextChangedListener(simpleWatcher { refreshPacePreview() })

        binding.btnSave.setOnClickListener { save() }
        binding.btnClear.setOnClickListener {
            GoalStore.clear(this)
            Toast.makeText(this, "Goal cleared", Toast.LENGTH_SHORT).show()
            finish()
        }

        refreshPacePreview()
    }

    private fun targetSeconds(): Int {
        val h = binding.editHours.text.toString().toIntOrNull() ?: 0
        val m = binding.editMinutes.text.toString().toIntOrNull() ?: 0
        val s = binding.editSeconds.text.toString().toIntOrNull() ?: 0
        return h * 3600 + m * 60 + s
    }

    private fun refreshPacePreview() {
        val secs = targetSeconds()
        if (secs <= 0) {
            binding.textPacePreview.text = "Pace: —"
            return
        }
        val pace = secs / (selectedMeters / 1000.0)
        val speedKmh = (selectedMeters / secs) * 3.6
        binding.textPacePreview.text = "Pace: ${formatPace(pace)}  •  %.1f km/h".format(speedKmh)
    }

    private fun save() {
        val secs = targetSeconds()
        if (secs <= 0) {
            Toast.makeText(this, "Enter a target time", Toast.LENGTH_SHORT).show()
            return
        }
        GoalStore.set(this, Goal(selectedMeters, secs))
        Toast.makeText(this, "Goal saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun simpleWatcher(onChange: () -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChange() }
        override fun afterTextChanged(s: android.text.Editable?) {}
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
