# Firebase Collaboration Setup Guide

## Overview
The Orion Code Editor now supports real-time collaboration using Firebase. Multiple users can work on the same project simultaneously with live file synchronization and presence tracking.

## Prerequisites
- An existing Firebase project (you mentioned you have one for the Android extension)
- Firebase Admin SDK service account credentials

## Setup Instructions

### Step 1: Get Firebase Service Account Credentials

1. Go to your [Firebase Console](https://console.firebase.google.com/)
2. Select your existing project (the one for your Android app)
3. Click the **gear icon** ⚙️ next to "Project Overview" → **Project settings**
4. Navigate to the **Service accounts** tab
5. Click **Generate new private key**
6. Save the downloaded JSON file as `firebase-credentials.json`

### Step 2: Add Credentials to Your Project

Place the `firebase-credentials.json` file in one of these locations:

**Option 1: Resources folder (recommended)**
```
src/main/resources/firebase-credentials.json
```

**Option 2: Project root**
```
c:\Users\User\IdeaProjects\-Orion-_2207025\firebase-credentials.json
```

**Important:** Add this file to `.gitignore` to keep credentials secure:
```gitignore
firebase-credentials.json
**/firebase-credentials.json
```

### Step 3: Configure Firestore Security Rules

In the Firebase Console:
1. Go to **Firestore Database**
2. Click the **Rules** tab
3. Add these security rules:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Projects collection
    match /projects/{projectId} {
      // Allow read if user is a member
      allow read: if request.auth != null && 
                    request.auth.uid in resource.data.memberIds;
      
      // Allow create for authenticated users
      allow create: if request.auth != null;
      
      // Allow update/delete only for project owner
      allow update, delete: if request.auth != null && 
                               request.auth.uid == resource.data.ownerId;
      
      // Files subcollection
      match /files/{fileId} {
        allow read, write: if request.auth != null && 
                             request.auth.uid in get(/databases/$(database)/documents/projects/$(projectId)).data.memberIds;
      }
      
      // Members subcollection
      match /members/{memberId} {
        allow read: if request.auth != null;
        allow write: if request.auth != null && 
                       request.auth.uid in get(/databases/$(database)/documents/projects/$(projectId)).data.memberIds;
      }
      
      // Typing indicators
      match /typing/{userId} {
        allow read, write: if request.auth != null;
      }
    }
  }
}
```

### Step 4: Build the Project

Run Maven to download Firebase dependencies:

```bash
mvn clean install
```

Or if using an IDE, reload Maven dependencies.

### Step 5: Run the Application

Launch Orion Code Editor. You should see:
```
Firebase initialized successfully.
Firebase collaboration features enabled
```

If Firebase credentials are not found, you'll see:
```
Firebase not configured - collaboration features disabled
To enable: place firebase-credentials.json in resources folder
```

## How to Use Collaboration Features

### Creating a Collaborative Project

1. Open a folder in Orion (File → Open Folder)
2. Click **File → Create Project**
3. Enter a project name
4. A unique share code will be generated (e.g., `ABC-123`)
5. Share this code with collaborators

### Joining a Project

1. Click **File → Join Project**
2. Enter the 6-character share code
3. The project workspace will open automatically
4. You'll see online collaborators in the status bar

### Features

✅ **Real-time File Sync**: Changes are synchronized every 2 seconds  
✅ **User Presence**: See who's online and what file they're editing  
✅ **Unique Share Codes**: 6-character codes like `ABC-123`  
✅ **Role-based Access**: Owner, Editor, Viewer roles  
✅ **Offline Support**: Works without Firebase, syncs when online  

## Firestore Data Structure

Your Firebase project will have this structure:

```
projects/
  {projectId}/
    - name: "My Project"
    - code: "ABC-123"
    - ownerId: "user123"
    - memberIds: ["user123", "user456"]
    - workspacePath: "C:/path/to/workspace"
    - createdAt: timestamp
    - updatedAt: timestamp
    
    members/
      {userId}/
        - username: "john_doe"
        - role: "OWNER" | "EDITOR" | "VIEWER"
        - isOnline: true
        - currentFile: "src/Main.java"
        - cursorPosition: 142
    
    files/
      {sanitizedFilePath}/
        - path: "src/Main.java"
        - content: "..."
        - lastModifiedBy: "user123"
        - lastModifiedAt: timestamp
    
    typing/
      {userId}/
        - filePath: "src/Main.java"
        - timestamp: timestamp
```

## Shared with Android App

Since you're using the same Firebase project for both desktop and Android:

- **Authentication**: Both apps share the same user database
- **Projects**: Android users can view/edit desktop projects
- **Sync**: Real-time sync works across platforms

**Note:** You may need to implement Firebase Authentication in the Android app for seamless user sync.

## Troubleshooting

### "Firebase not initialized" error
- Check that `firebase-credentials.json` exists in `src/main/resources/`
- Verify the JSON file is valid (open in text editor)
- Ensure Maven dependencies are downloaded (`mvn clean install`)

### "Permission denied" in Firestore
- Check Firestore security rules are configured correctly
- Verify user is authenticated before creating/joining projects

### Real-time sync not working
- Check internet connection
- Verify Firestore is enabled in Firebase Console
- Look for errors in the console output

### Android integration issues
- Ensure both apps use the same Firebase project
- Implement Firebase Auth in Android app
- Use same Firestore collection structure

## Cost Considerations

Firebase Firestore has a free tier:
- **Storage:** 1 GB free
- **Document reads:** 50,000/day free
- **Document writes:** 20,000/day free

To reduce costs:
- File sync is debounced to 2 seconds
- Only active files are synchronized
- Consider upgrading to Blaze plan for production use

## Next Steps

1. ✅ Set up Firebase credentials
2. ✅ Configure Firestore security rules
3. ✅ Test creating a project
4. ✅ Test joining with a friend
5. Implement conflict resolution (future enhancement)
6. Add chat feature (future enhancement)
7. Add version history (future enhancement)

## Security Best Practices

1. **Never commit `firebase-credentials.json` to Git**
2. Use environment variables for production
3. Implement proper authentication
4. Regularly rotate service account keys
5. Monitor Firebase usage in the console

For more help, see the [Firebase Documentation](https://firebase.google.com/docs).
