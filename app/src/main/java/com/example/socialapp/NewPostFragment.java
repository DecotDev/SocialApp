package com.example.socialapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.User;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;

public class NewPostFragment extends Fragment {
    Button publishButton;
    EditText postContentEditText;
    NavController navController;
    Client client;
    Account account;

    AppViewModel appViewModel;
    Uri mediaUri;
    String mediaTipo;

    @androidx.annotation.Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @androidx.annotation.Nullable ViewGroup container, @androidx.annotation.Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_new_post, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);

        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        publishButton = view.findViewById(R.id.publishButton);
        postContentEditText = view.findViewById(R.id.postContentEditText);

        // Agregar un TextWatcher para detectar hashtags y menciones
        postContentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString();
                detectHashtagsAndMentions(text);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        publishButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                publicar();
            }
        });

        view.findViewById(R.id.camara_fotos).setOnClickListener(v -> tomarFoto());
        view.findViewById(R.id.camara_video).setOnClickListener(v -> tomarVideo());
        view.findViewById(R.id.grabar_audio).setOnClickListener(v -> grabarAudio());
        view.findViewById(R.id.imagen_galeria).setOnClickListener(v -> seleccionarImagen());
        view.findViewById(R.id.video_galeria).setOnClickListener(v -> seleccionarVideo());
        view.findViewById(R.id.audio_galeria).setOnClickListener(v -> seleccionarAudio());
        appViewModel.mediaSeleccionado.observe(getViewLifecycleOwner(), media -> {
            this.mediaUri = media.uri;
            this.mediaTipo = media.tipo;
            Glide.with(this).load(media.uri).into((ImageView) view.findViewById(R.id.previsualizacion));
        });
    }

    private void publicar() {
        String postContent = postContentEditText.getText().toString();
        if (TextUtils.isEmpty(postContent)) {
            postContentEditText.setError("Required");
            return;
        }
        publishButton.setEnabled(false);

        // Obtenemos información de la cuenta del autor
        account = new Account(client);
        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                if (mediaTipo == null) {
                    guardarEnAppWrite(result, postContent, null);
                } else {
                    guardarEnAppWrite(result, postContent,mediaTipo);
                }
            }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }

    void guardarEnAppWrite(User<Map<String, Object>> user, String content, String mediaUrl) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        // Crear instancia del servicio Databases
        Databases databases = new Databases(client);

        // Datos del documento
        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getId().toString());
        data.put("author", user.getName().toString());
        data.put("authorPhotoUrl", null);
        data.put("content", content);
        data.put("mediaType", mediaTipo);
        data.put("mediaUrl", mediaUrl);
        data.put("time", Calendar.getInstance().getTimeInMillis());

        // Extraer hashtags y mentions del contenido del post
        List<String> hashtags = extractHashtags(content);
        List<String> mentions = extractMentions(content);
        data.put("hashtags", hashtags);
        data.put("mentions", mentions);

        try {
            databases.createDocument(
                    getString(R.string.APPWRITE_DATABASE_ID),
                    getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                    "unique()", // Generar un ID único automáticamente
                    data,
                    new ArrayList<>(), // Permisos opcionales, como ["role:all"]
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            Snackbar.make(requireView(), "Error: " + error.toString(), Snackbar.LENGTH_LONG).show();
                        } else {
                            System.out.println("Post creado:" + result.toString());
                            mainHandler.post(() -> {
                                navController.popBackStack();
                            });
                        }
                    })
            );
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }

    // Método para detectar hashtags y menciones en el texto
    void detectHashtagsAndMentions(String text) {
        List<String> hashtags = extractHashtags(text);
        List<String> mentions = extractMentions(text);

        // Aquí puedes hacer algo con los hashtags y mentions detectados, como mostrarlos en la UI
        System.out.println("Hashtags detectados: " + hashtags);
        System.out.println("Menciones detectadas: " + mentions);
    }

    // Método para extraer hashtags del texto
    List<String> extractHashtags(String text) {
        List<String> hashtags = new ArrayList<>();
        Pattern pattern = Pattern.compile("#(\\w+)");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            hashtags.add(matcher.group(1));
        }
        return hashtags;
    }

    // Método para extraer menciones del texto
    List<String> extractMentions(String text) {
        List<String> mentions = new ArrayList<>();
        Pattern pattern = Pattern.compile("@(\\w+)");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return mentions;
    }

    // Métodos para manejar la selección de medios (fotos, videos, audio)
    private final ActivityResultLauncher<String> galeria =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> {
                        appViewModel.setMediaSeleccionado(uri, mediaTipo);
                    });

    private final ActivityResultLauncher<Uri> camaraFotos =
            registerForActivityResult(new ActivityResultContracts.TakePicture(),
                    isSuccess -> {
                        appViewModel.setMediaSeleccionado(mediaUri, "image");
                    });

    private final ActivityResultLauncher<Uri> camaraVideos =
            registerForActivityResult(new ActivityResultContracts.TakeVideo(),
                    isSuccess -> {
                        appViewModel.setMediaSeleccionado(mediaUri, "video");
                    });

    private final ActivityResultLauncher<Intent> grabadoraAudio =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    appViewModel.setMediaSeleccionado(result.getData().getData(), "audio");
                }
            });

    private void seleccionarImagen() {
        mediaTipo = "image";
        galeria.launch("image/*");
    }

    private void seleccionarVideo() {
        mediaTipo = "video";
        galeria.launch("video/*");
    }

    private void seleccionarAudio() {
        mediaTipo = "audio";
        galeria.launch("audio/*");
    }

    private void tomarFoto() {
        try {
            mediaUri = FileProvider.getUriForFile(requireContext(),
                    "com.example.puigtagram" + ".fileprovider",
                    File.createTempFile("img", ".jpg",
                            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES))
            );
            camaraFotos.launch(mediaUri);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void tomarVideo() {
        try {
            mediaUri = FileProvider.getUriForFile(requireContext(),
                    "com.example.puigtagram" + ".fileprovider",
                    File.createTempFile("vid", ".mp4",
                            requireContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES)));
            camaraVideos.launch(mediaUri);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void grabarAudio() {
        grabadoraAudio.launch(new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION));
    }
}