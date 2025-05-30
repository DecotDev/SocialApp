package com.example.socialapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.appwrite.Client;
import io.appwrite.Query;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.DocumentList;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;

public class HomeFragment extends Fragment {

    NavController navController;
    ImageView photoImageView;
    TextView displayNameTextView, emailTextView;
    Client client;
    Account account;
    String userId;
    PostsAdapter adapter;

    AppViewModel appViewModel;


    public HomeFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        navController = Navigation.findNavController(view);
        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        NavigationView navigationView = view.getRootView().findViewById(R.id.nav_view);
        View header = navigationView.getHeaderView(0);
        photoImageView = header.findViewById(R.id.imageView);
        displayNameTextView = header.findViewById(R.id.displayNameTextView);
        emailTextView = header.findViewById(R.id.emailTextView);


        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                mainHandler.post(() -> {
                    userId = result.getId();
                    displayNameTextView.setText(result.getName().toString());
                    emailTextView.setText(result.getEmail().toString());
                    Glide.with(requireView()).load(R.drawable.user).into(photoImageView);
                    obtenerPosts(); // < – Pedir los posts tras obtener el usuario
                });
            }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }

        view.findViewById(R.id.gotoNewPostFragmentButton).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                navController.navigate(R.id.newPostFragment);
            }
        });

        RecyclerView postsRecyclerView = view.findViewById(R.id.postsRecyclerView);
        adapter = new PostsAdapter();
        postsRecyclerView.setAdapter(adapter);

    }

    void obtenerPosts() {
        Databases databases = new Databases(client);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        try {
            databases.listDocuments(getString(R.string.APPWRITE_DATABASE_ID), // databaseId
                    getString(R.string.APPWRITE_POSTS_COLLECTION_ID), // collectionId
                    //new ArrayList<>(), // queries (optional)
                    Arrays.asList(Query.Companion.orderDesc("time"), Query.Companion.limit(50)),

                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            Snackbar.make(requireView(), "Error al obtener los posts: " + error.toString(), Snackbar.LENGTH_LONG).show();
                            return;
                        }
                        System.out.println(result.toString());
                        mainHandler.post(() -> adapter.establecerLista(result));
                    }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }


    class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView authorPhotoImageView, likeImageView, mediaImageView;
        TextView authorTextView, contentTextView, numLikesTextView, timeTextView;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            authorPhotoImageView = itemView.findViewById(R.id.authorPhotoImageView);
            likeImageView = itemView.findViewById(R.id.likeImageView);
            mediaImageView = itemView.findViewById(R.id.mediaImage);
            authorTextView = itemView.findViewById(R.id.authorTextView);
            contentTextView = itemView.findViewById(R.id.contentTextView);
            numLikesTextView = itemView.findViewById(R.id.numLikesTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
        }
    }

    class PostsAdapter extends RecyclerView.Adapter<PostViewHolder> {
        DocumentList<Map<String, Object>> lista = null;

        @NonNull
        @Override
        public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PostViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.viewholder_post, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
            Map<String, Object> post = lista.getDocuments().get(position).getData();



            // Set default profile picture while fetching the actual one
            holder.authorPhotoImageView.setImageResource(R.drawable.user);

            // Ensure post contains a valid user ID
            if (post.get("uid") != null) {
                String userId = post.get("uid").toString();

                // Fetch profile picture from ProfilePictures collection
                Databases databases = new Databases(client);
                try {
                    databases.listDocuments(
                            getString(R.string.APPWRITE_DATABASE_ID),
                            getString(R.string.APPWRITE_PROFILE_PICTURES_COLLECTION_ID),
                            Arrays.asList(Query.Companion.equal("userId", userId)), // Fix: Use Query.Companion.equal()
                            new CoroutineCallback<>((result, error) -> {
                                if (error != null || result == null || result.getDocuments().isEmpty()) {
                                    return; // No profile picture found, keep default
                                }

                                String imageUrl = result.getDocuments().get(0).getData().get("imageUrl").toString();
                                holder.itemView.post(() ->  // Fix: Use post() to update UI safely
                                        Glide.with(holder.itemView.getContext())
                                                .load(imageUrl)
                                                .circleCrop()
                                                .into(holder.authorPhotoImageView)
                                );
                            }));
                } catch (AppwriteException e) {
                    e.printStackTrace();
                }
            }

            // Get references to the new TextViews
            TextView hashtagsTextView = holder.itemView.findViewById(R.id.hashtagsTextView);
            TextView mentionsTextView = holder.itemView.findViewById(R.id.mentionsTextView);

            // Fetch hashtags and mentions
            List<String> hashtags = (List<String>) post.get("hashtags");
            List<String> mentions = (List<String>) post.get("mentions");

            // Display Hashtags
            if (hashtags != null && !hashtags.isEmpty()) {
                hashtagsTextView.setText("#" + String.join(", #", hashtags));
                hashtagsTextView.setVisibility(View.VISIBLE);
            } else {
                hashtagsTextView.setVisibility(View.GONE);
            }

            // Display Mentions
            if (mentions != null && !mentions.isEmpty()) {
                mentionsTextView.setText("@" + String.join(", @", mentions));
                mentionsTextView.setVisibility(View.VISIBLE);
            } else {
                mentionsTextView.setVisibility(View.GONE);
            }

            holder.authorTextView.setText(post.get("author").toString());
            holder.contentTextView.setText(post.get("content").toString());

            // Make the profile picture clickable to navigate to the user's profile
            holder.authorPhotoImageView.setOnClickListener(view -> {
                Bundle bundle = new Bundle();
                bundle.putString("profileUserId", post.get("uid").toString());
                navController.navigate(R.id.profileFragment, bundle);
            });

            // Format the post timestamp
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            Calendar calendar = Calendar.getInstance();
            if (post.get("time") != null) {
                calendar.setTimeInMillis((long) post.get("time"));
            } else {
                calendar.setTimeInMillis(0);
            }
            holder.timeTextView.setText(formatter.format(calendar.getTime()));

            // Handle Likes
            List<String> likes = (List<String>) post.get("likes");
            if (likes.contains(userId)) {
                holder.likeImageView.setImageResource(R.drawable.like_on);
            } else {
                holder.likeImageView.setImageResource(R.drawable.like_off);
            }

            holder.numLikesTextView.setText(String.valueOf(likes.size()));

            holder.likeImageView.setOnClickListener(view -> {
                Databases databases = new Databases(client);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                List<String> nuevosLikes = new ArrayList<>(likes);

                if (nuevosLikes.contains(userId)) {
                    nuevosLikes.remove(userId);
                } else {
                    nuevosLikes.add(userId);
                }

                Map<String, Object> data = new HashMap<>();
                data.put("likes", nuevosLikes);

                try {
                    databases.updateDocument(getString(R.string.APPWRITE_DATABASE_ID),
                            getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                            post.get("$id").toString(),
                            data,
                            new ArrayList<>(),
                            new CoroutineCallback<>((result, error) -> {
                                if (error != null) {
                                    error.printStackTrace();
                                    return;
                                }
                                System.out.println("Likes actualizados: " + result.toString());
                                mainHandler.post(() -> obtenerPosts());
                            }));
                } catch (AppwriteException e) {
                    throw new RuntimeException(e);
                }
            });

            // Display media (if exists)
            if (post.get("mediaUrl") != null) {
                holder.mediaImageView.setVisibility(View.VISIBLE);
                if ("audio".equals(post.get("mediaType").toString())) {
                    Glide.with(requireView()).load(R.drawable.audio).centerCrop().into(holder.mediaImageView);
                } else {
                    Glide.with(requireView()).load(post.get("mediaUrl").toString()).centerCrop().into(holder.mediaImageView);
                }
                holder.mediaImageView.setOnClickListener(view -> {
                    appViewModel.postSeleccionado.setValue(post);
                    navController.navigate(R.id.mediaFragment);
                });
            } else {
                holder.mediaImageView.setVisibility(View.GONE);
            }
        }
        @Override
        public int getItemCount() {
            return lista == null ? 0 : lista.getDocuments().size();
        }

        public void establecerLista(DocumentList<Map<String, Object>> lista) {
            this.lista = lista;
            notifyDataSetChanged();
        }
    }


}