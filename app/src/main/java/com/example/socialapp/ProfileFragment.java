package com.example.socialapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.appwrite.Client;
import io.appwrite.Query;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.DocumentList;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;

public class ProfileFragment extends Fragment {
    private NavController navController;
    private ImageView photoImageView;
    private TextView displayNameTextView, emailTextView;
    private Button followButton;

    private String profileUserId, currentUserId;
    private boolean isFollowing = false;

    private Client client;
    private Account account;
    private Databases databases;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);

        photoImageView = view.findViewById(R.id.photoImageView);
        displayNameTextView = view.findViewById(R.id.displayNameTextView);
        emailTextView = view.findViewById(R.id.emailTextView);
        followButton = view.findViewById(R.id.followButton);

        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);
        databases = new Databases(client);

        // Get the logged-in user ID
        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) return;

                currentUserId = result.getId();

                // Check if visiting another user's profile
                if (getArguments() != null && getArguments().containsKey("profileUserId")) {
                    profileUserId = getArguments().getString("profileUserId");
                } else {
                    profileUserId = currentUserId; // If opened from menu, show own profile
                }

                try {
                    fetchUserProfile(); // Fetch user data after setting profileUserId
                } catch (AppwriteException e) {
                    throw new RuntimeException(e);
                }
            }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }

        followButton.setOnClickListener(v -> {
            if (isFollowing) {
                try {
                    unfollowUser();
                } catch (AppwriteException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                try {
                    followUser();
                } catch (AppwriteException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void fetchUserProfile() throws AppwriteException {
        // Get the current user ID
        account.get(new CoroutineCallback<>((result, error) -> {
            if (error != null) return;

            currentUserId = result.getId();  // Store the logged-in user ID

            // Check if the fragment was opened with another user's profile ID
            if (getArguments() != null && getArguments().containsKey("profileUserId")) {
                profileUserId = getArguments().getString("profileUserId");
            } else {
                profileUserId = currentUserId; // Default to own profile
            }

            // Fetch and display user data
            requireActivity().runOnUiThread(() -> {
                displayNameTextView.setText(result.getName());
                emailTextView.setText(result.getEmail());
                try {
                    fetchProfilePicture();  // Fetch profile picture
                } catch (AppwriteException e) {
                    throw new RuntimeException(e);
                }
            });

            try {
                checkIfFollowing();  // Check if the user is followed
            } catch (AppwriteException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    private void fetchProfilePicture() throws AppwriteException {
        databases.listDocuments(getString(R.string.APPWRITE_DATABASE_ID),
                getString(R.string.APPWRITE_PROFILE_PICTURES_COLLECTION_ID),
                Arrays.asList(Query.Companion.equal("userId", profileUserId)),
                new CoroutineCallback<>((result, error) -> {
                    if (error != null || result.getDocuments().isEmpty()) {
                        // No profile picture found, set default
                        requireActivity().runOnUiThread(() ->
                                photoImageView.setImageResource(R.drawable.user) // user.xml
                        );
                        return;
                    }

                    // If profile picture exists, load it with Glide
                    String imageUrl = result.getDocuments().get(0).getData().get("imageUrl").toString();
                    requireActivity().runOnUiThread(() ->
                            Glide.with(requireView()).load(imageUrl).circleCrop().into(photoImageView)
                    );
                }));
    }

    /*private void fetchProfilePicture() throws AppwriteException {
        databases.listDocuments(getString(R.string.APPWRITE_DATABASE_ID),
                getString(R.string.APPWRITE_PROFILE_PICTURES_COLLECTION_ID),
                Arrays.asList(Query.Companion.equal("userId", profileUserId)),
                new CoroutineCallback<>((result, error) -> {
                    if (error != null || result.getDocuments().isEmpty()) return;

                    String imageUrl = result.getDocuments().get(0).getData().get("imageUrl").toString();
                    requireActivity().runOnUiThread(() ->
                            Glide.with(requireView()).load(imageUrl).circleCrop().into(photoImageView)
                    );
                }));
    }*/

    private void checkIfFollowing() throws AppwriteException {
        databases.listDocuments(getString(R.string.APPWRITE_DATABASE_ID),
                getString(R.string.APPWRITE_FOLLOWERS_COLLECTION_ID),
                Arrays.asList(Query.Companion.equal("followerId", currentUserId),
                        Query.Companion.equal("followingId", profileUserId)),
                new CoroutineCallback<>((result, error) -> {
                    if (error != null) return;
                    isFollowing = !result.getDocuments().isEmpty();
                    requireActivity().runOnUiThread(() -> followButton.setText(isFollowing ? "Unfollow" : "Follow"));
                }));
    }

    private void followUser() throws AppwriteException {
        Map<String, Object> data = new HashMap<>();
        data.put("followerId", currentUserId);
        data.put("followingId", profileUserId);

        databases.createDocument(getString(R.string.APPWRITE_DATABASE_ID),
                getString(R.string.APPWRITE_FOLLOWERS_COLLECTION_ID),
                "unique()",
                data,
                new CoroutineCallback<>((result, error) -> {
                    if (error != null) return;
                    isFollowing = true;
                    requireActivity().runOnUiThread(() -> followButton.setText("Unfollow"));
                }));
    }

    private void unfollowUser() throws AppwriteException {
        databases.listDocuments(getString(R.string.APPWRITE_DATABASE_ID),
                getString(R.string.APPWRITE_FOLLOWERS_COLLECTION_ID),
                Arrays.asList(Query.Companion.equal("followerId", currentUserId),
                        Query.Companion.equal("followingId", profileUserId)),
                new CoroutineCallback<>((result, error) -> {
                    if (error != null || result.getDocuments().isEmpty()) return;

                    String documentId = result.getDocuments().get(0).getId();
                    databases.deleteDocument(getString(R.string.APPWRITE_DATABASE_ID),
                            getString(R.string.APPWRITE_FOLLOWERS_COLLECTION_ID),
                            documentId,
                            new CoroutineCallback<>((delResult, delError) -> {
                                if (delError != null) return;
                                isFollowing = false;
                                requireActivity().runOnUiThread(() -> followButton.setText("Follow"));
                            }));
                }));
    }
}