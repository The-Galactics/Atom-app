package com.atom.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.airbnb.lottie.LottieAnimationView;

public class MainActivity extends AppCompatActivity {

    private LottieAnimationView atomCore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Reference to the Atom Core (The pulsating animation)
        atomCore = findViewById(R.id.atom_core_animation);
        
        // This is where we will connect with your Spring Boot API in the future
        // and change the animation state based on the response.
    }
}
