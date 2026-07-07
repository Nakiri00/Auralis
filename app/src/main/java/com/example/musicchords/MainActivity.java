package com.example.musicchords;

import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.musicchords.databinding.ActivityMainBinding;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;

    private Fragment homeFragment;
    private Fragment historyFragment;
    private Fragment libraryFragment;
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        binding.navMenu.setEnabled(false);

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main),
            (v, insets) -> {
                Insets sb = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                );
                v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
                return insets;
            }
        );

        // Observe auth state dari ViewModel
        viewModel
            .getAuthState()
            .observe(this, state -> {
                switch (state) {
                    case SUCCESS:
                        setupFragments();
                        break;
                    case FAILED:
                        Toast.makeText(
                            this,
                            "Gagal Login Database.",
                            Toast.LENGTH_SHORT
                        ).show();
                        break;
                    case LOADING:
                        break;
                }
            });

        viewModel.ensureAuthenticated();
    }

    private void setupFragments() {
        var fm = getSupportFragmentManager();

        homeFragment = fm.findFragmentByTag("HOME");
        historyFragment = fm.findFragmentByTag("HISTORY");
        libraryFragment = fm.findFragmentByTag("LIBRARY");

        if (homeFragment == null) {
            homeFragment = new HomeFragment();
            historyFragment = new HistoryFragment();
            libraryFragment = new LibraryFragment();

            fm.beginTransaction()
                    .add(R.id.main_frame, homeFragment, "HOME")
                    .add(R.id.main_frame, historyFragment, "HISTORY").hide(historyFragment)
                    .add(R.id.main_frame, libraryFragment, "LIBRARY").hide(libraryFragment)
                    .commit();

            activeFragment = homeFragment;
        } else {
            if (!libraryFragment.isHidden()) {
                activeFragment = libraryFragment;
            } else if (!historyFragment.isHidden()) {
                activeFragment = historyFragment;
            } else {
                activeFragment = homeFragment;
            }
        }

        // Jalankan listener navigasi
        binding.navMenu.setEnabled(true);
        binding.navMenu.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.home) {
                showHideFragment(homeFragment);
                return true;
            } else if (id == R.id.library) {
                showHideFragment(libraryFragment);
                return true;
            } else if (id == R.id.history) {
                showHideFragment(historyFragment);
                return true;
            }
            return false;
        });
    }

    private void showHideFragment(Fragment target) {
        if (target == activeFragment) return;
        getSupportFragmentManager()
            .beginTransaction()
            .hide(activeFragment)
            .show(target)
            .commit();
        activeFragment = target;
    }

    public void playSongFromHistory(Bundle bundle) {
        binding.navMenu.setSelectedItemId(R.id.home);
        ((HomeFragment) homeFragment).loadHistoryData(bundle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // Cari item menu ganti bahasa
        MenuItem langItem = menu.findItem(R.id.action_change_language);
        if (langItem != null) {
            // Cek bahasa saat ini
            LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
            boolean isCurrentlyIndonesian = currentLocales.isEmpty() ||
                    currentLocales.get(0).getLanguage().equals("in") ||
                    currentLocales.get(0).getLanguage().equals("id");

            // Atur teks sesuai bahasa yang aktif
            if (isCurrentlyIndonesian) {
                langItem.setTitle("ID");
            } else {
                langItem.setTitle("EN");
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_change_language) {

            LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
            boolean isCurrentlyIndonesian = currentLocales.isEmpty() ||
                    currentLocales.get(0).getLanguage().equals("in") ||
                    currentLocales.get(0).getLanguage().equals("id");

            if (isCurrentlyIndonesian) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"));
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("id"));
            }

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateActivityTexts();
        updateActiveFragmentTexts();

        invalidateOptionsMenu();
    }

    private void updateActivityTexts() {
        // Update Judul Toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.app_name));
        }

        // Update Bottom Navigation Titles (Pastikan Anda sudah punya string di strings.xml untuk menu_home, menu_library, menu_history)
        // Jika tidak, Anda bisa melewati update Bottom Nav ini atau membuatnya jika perlu.
         binding.navMenu.getMenu().findItem(R.id.home).setTitle(getString(R.string.menu_home));
         binding.navMenu.getMenu().findItem(R.id.library).setTitle(getString(R.string.menu_library));
         binding.navMenu.getMenu().findItem(R.id.history).setTitle(getString(R.string.menu_history));
    }

    private void updateActiveFragmentTexts() {
        if (activeFragment instanceof HomeFragment) {
            ((HomeFragment) activeFragment).updateTexts();
        } else if (activeFragment instanceof HistoryFragment) {
            ((HistoryFragment) activeFragment).updateTexts();
        }
    }


}
