package com.fengshuimaster.app;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.fengshuimaster.app.databinding.ActivityMainBinding;
import com.fengshuimaster.app.ui.FengShuiFragment;
import com.fengshuimaster.app.ui.PalmAiFragment;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null) {
            showFragment(new FengShuiFragment());
        }

        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_feng_shui) {
                showFragment(new FengShuiFragment());
                return true;
            } else if (id == R.id.nav_palm_ai) {
                showFragment(new PalmAiFragment());
                return true;
            }
            return false;
        });
    }

    private void showFragment(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
