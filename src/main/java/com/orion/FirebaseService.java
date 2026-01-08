package com.orion;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Service for initializing and managing Firebase connections.
 * Provides access to Firebase Authentication and Firestore database.
 */
public class FirebaseService {
    private static FirebaseService instance;
    private Firestore firestore;
    private FirebaseAuth auth;
    private boolean initialized = false;
    private String webApiKey; // For password verification

    private FirebaseService() {
        // Private constructor for singleton
    }

    public static FirebaseService getInstance() {
        if (instance == null) {
            instance = new FirebaseService();
        }
        return instance;
    }

    /**
     * Initialize Firebase with service account credentials.
     * The credentials file should be placed in the resources folder or a specific path.
     * 
     * @param credentialsPath Path to the Firebase service account JSON file
     * @throws IOException If credentials file is not found or invalid
     */
    public void initialize(String credentialsPath) throws IOException {
        if (initialized) {
            System.out.println("Firebase already initialized.");
            return;
        }

        try {
            InputStream serviceAccount;
            
            // Try to load from file system first
            try {
                serviceAccount = new FileInputStream(credentialsPath);
            } catch (IOException e) {
                // Try to load from resources
                serviceAccount = getClass().getClassLoader().getResourceAsStream(credentialsPath);
                if (serviceAccount == null) {
                    throw new IOException("Firebase credentials file not found: " + credentialsPath);
                }
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            
            this.firestore = FirestoreClient.getFirestore();
            this.auth = FirebaseAuth.getInstance();
            this.initialized = true;
            
            // Set Web API Key for password verification (from Firebase Console)
            // Get this from: Firebase Console > Project Settings > General > Web API Key
            this.webApiKey = "AIzaSyBzhLFuMBgk17MBwVQmOJzAfJdE-uuZoKw"; // Update with your actual key
            
            System.out.println("Firebase initialized successfully.");
        } catch (Exception e) {
            System.err.println("Failed to initialize Firebase: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Initialize Firebase with default credentials path.
     * Looks for 'firebase-credentials.json' in the resources folder.
     */
    public void initialize() throws IOException {
        initialize("firebase-credentials.json");
    }

    /**
     * Get Firestore database instance.
     * @return Firestore instance
     */
    public Firestore getFirestore() {
        if (!initialized) {
            throw new IllegalStateException("Firebase not initialized. Call initialize() first.");
        }
        return firestore;
    }

    /**
     * Get Firebase Authentication instance.
     * @return FirebaseAuth instance
     */
    public FirebaseAuth getAuth() {
        if (!initialized) {
            throw new IllegalStateException("Firebase not initialized. Call initialize() first.");
        }
        return auth;
    }

    /**
     * Check if Firebase has been initialized.
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Get Web API Key for password verification.
     * @return Web API Key
     */
    public String getWebApiKey() {
        return webApiKey;
    }

    /**
     * Shutdown Firebase connection.
     */
    public void shutdown() {
        if (initialized) {
            try {
                firestore.close();
                initialized = false;
                System.out.println("Firebase connection closed.");
            } catch (Exception e) {
                System.err.println("Error closing Firebase: " + e.getMessage());
            }
        }
    }
}
