package com.nakiri00.auralis;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class LibraryFragment extends Fragment {

    private LibraryViewModel viewModel;
    private ChordGroupAdapter adapter;
    private RecyclerView rvChords;
    private ProgressBar progressBar;

    @Override
    public View onCreateView(
        LayoutInflater inflater,
        ViewGroup container,
        Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(
        @NonNull View view,
        @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);

        rvChords = view.findViewById(R.id.rv_chords);
        progressBar = view.findViewById(R.id.progress_bar_library);

        // Setup RecyclerView
        rvChords.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChordGroupAdapter();
        adapter.setOnPlayListener(group -> viewModel.playAudio(group));
        rvChords.setAdapter(adapter);

        setupObservers();
        viewModel.loadChords(); // idempotent
    }

    private void setupObservers() {
        viewModel
            .getIsLoading()
            .observe(getViewLifecycleOwner(), loading -> {
                progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
                rvChords.setVisibility(loading ? View.GONE : View.VISIBLE);
            });

        viewModel
            .getChordGroups()
            .observe(getViewLifecycleOwner(), groups ->
                adapter.updateData(groups)
            );

        viewModel
            .getToastMessage()
            .observe(getViewLifecycleOwner(), msg -> {
                if (msg != null && !msg.isEmpty() && isAdded()) {
                    Toast.makeText(
                        requireContext(),
                        msg,
                        Toast.LENGTH_SHORT
                    ).show();
                    viewModel.clearToastMessage();
                }
            });
    }
}
