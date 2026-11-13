package com.example.thoikhoabieu;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.thoikhoabieu.notify.ReminderScheduler;
import com.example.thoikhoabieu.ui.DetailFragment;
import com.example.thoikhoabieu.ui.HomeFragment;
import com.example.thoikhoabieu.ui.SettingsFragment;
import com.example.thoikhoabieu.ui.StatsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView nav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            show(item.getItemId());
            return true;
        });
        if (savedInstanceState == null) nav.setSelectedItemId(R.id.nav_home);
        ReminderScheduler.scheduleNext(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.example.thoikhoabieu.trash.TrashManager.checkAndPromptCleanup(this);
    }

    private void show(int id) {
        Fragment f;
        if (id == R.id.nav_detail) f = new DetailFragment();
        else if (id == R.id.nav_stats) f = new StatsFragment();
        else if (id == R.id.nav_settings) f = new SettingsFragment();
        else f = new HomeFragment();
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, f).commit();
    }

    /** Cho các fragment chuyển tab. */
    public void openTab(int menuId) {
        nav.setSelectedItemId(menuId);
    }
}
