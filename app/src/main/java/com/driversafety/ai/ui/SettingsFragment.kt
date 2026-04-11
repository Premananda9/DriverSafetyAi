package com.driversafety.ai.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.driversafety.ai.databinding.FragmentSettingsBinding
import com.driversafety.ai.utils.AppPreferenceManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: AppPreferenceManager

    // Music picker launcher
    private val musicPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                handleMusicSelected(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = AppPreferenceManager(requireContext())

        loadCurrentSettings()
        setupListeners()
    }

    private fun loadCurrentSettings() {
        binding.etContactName.setText(prefs.contactName)
        binding.etContactPhone.setText(prefs.contactPhone)
        binding.switchAutoCall.isChecked = prefs.autoCallEnabled
        binding.switchVoiceInteraction.isChecked = prefs.voiceInteractionEnabled

        if (prefs.hasCustomMusic()) {
            val uri = prefs.getMusicUriOrNull()
            val name = getFileNameFromUri(uri)
            binding.tvSelectedMusic.text = "🎵 $name"
        } else {
            binding.tvSelectedMusic.text = "🎵 Default alert tone"
        }
    }

    private fun setupListeners() {
        // Save emergency contact
        binding.btnSaveContact.setOnClickListener {
            val name = binding.etContactName.text.toString().trim()
            val phone = binding.etContactPhone.text.toString().trim()

            if (phone.isEmpty()) {
                binding.etContactPhone.error = "Phone number is required"
                return@setOnClickListener
            }

            prefs.contactName = name
            prefs.contactPhone = phone
            Toast.makeText(context, "✅ Emergency contact saved", Toast.LENGTH_SHORT).show()
        }

        // Choose music from library
        binding.btnChooseMusic.setOnClickListener {
            openMusicPicker()
        }

        // Toggles
        binding.switchAutoCall.setOnCheckedChangeListener { _, checked ->
            prefs.autoCallEnabled = checked
        }

        binding.switchVoiceInteraction.setOnCheckedChangeListener { _, checked ->
            prefs.voiceInteractionEnabled = checked
        }



    }

    private fun openMusicPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav"))
        }
        musicPickerLauncher.launch(intent)
    }

    private fun handleMusicSelected(uri: Uri) {
        // Persist access permission across reboots
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Non-fatal
        }

        prefs.musicUri = uri.toString()
        val name = getFileNameFromUri(uri)
        binding.tvSelectedMusic.text = "🎵 $name"
        Toast.makeText(context, "Music saved: $name", Toast.LENGTH_SHORT).show()
    }

    private fun getFileNameFromUri(uri: Uri?): String {
        if (uri == null) return "Default"
        return try {
            val cursor = requireContext().contentResolver.query(
                uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(0)
                } else uri.lastPathSegment ?: "Selected file"
            } ?: uri.lastPathSegment ?: "Selected file"
        } catch (e: Exception) {
            uri.lastPathSegment ?: "Selected file"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
