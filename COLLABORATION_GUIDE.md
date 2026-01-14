# 🚀 Quick Start: Using Collaboration Features

## Where to Find Collaboration Features

After launching Orion Code Editor, you'll find a new **"Collaborate"** menu in the menu bar:

```
File | Edit | Run | 🆕 Collaborate | View | Settings
```

## Menu Options

### 📋 Collaborate Menu

1. **Create Project** (Ctrl+Shift+P)
   - Creates a new collaborative project from your current folder
   - Generates a unique 6-character share code
   - Makes you the project owner

2. **Join Project** (Ctrl+Shift+J)
   - Join an existing project using a share code
   - Opens the project workspace automatically
   - Starts real-time sync

3. **View Online Members**
   - Shows all project members
   - Displays who's online
   - Shows what file each person is editing
   - Indicates roles (Owner 👑, Editor ✏️, Viewer 👁️)

## Step-by-Step Usage

### 🎯 Creating Your First Collaborative Project

1. **Open a folder**
   - File → Open Folder
   - Select your project directory

2. **Create project**
   - Collaborate → Create Project (or press `Ctrl+Shift+P`)
   - Enter a project name
   - **Copy the share code** (e.g., `ABC-123`)

3. **Share the code**
   - Send the code to your collaborators via chat/email
   - They can join using this code

### 🤝 Joining a Project

1. **Join via code**
   - Collaborate → Join Project (or press `Ctrl+Shift+J`)
   - Enter the 6-character code
   - Workspace opens automatically

2. **Start collaborating**
   - Open any file in the project
   - Changes sync automatically every 2 seconds
   - See online members in the status bar

### 👥 Viewing Collaborators

- Click **Collaborate → View Online Members**
- See:
  - 🟢 Online / ⚫ Offline status
  - Current file being edited
  - Role (Owner/Editor/Viewer)
  - Username

## Status Bar Indicators

When collaborating, the status bar shows:
```
📡 3 online: alice, bob, charlie
```

This updates in real-time as people join/leave.

## Before First Use

⚠️ **Firebase Setup Required**

If you haven't set up Firebase yet, you'll see:
```
Collaboration Unavailable
Firebase is not configured. Please set up Firebase credentials.
```

Follow the [FIREBASE_SETUP.md](FIREBASE_SETUP.md) guide to:
1. Download Firebase credentials from your Firebase Console
2. Save as `src/main/resources/firebase-credentials.json`
3. Restart the application

## Keyboard Shortcuts

| Action | Shortcut |
|--------|----------|
| Create Project | `Ctrl+Shift+P` |
| Join Project | `Ctrl+Shift+J` |

## Tips

✅ **DO:**
- Share the project code via secure channels
- Check "View Online Members" to see who's editing
- Save your files regularly (Ctrl+S)

❌ **DON'T:**
- Share Firebase credentials (only the project code)
- Edit the same line simultaneously (conflicts may occur)
- Commit the project code to public repositories

## Troubleshooting

### "Collaboration Unavailable" message
→ Firebase not configured. See [FIREBASE_SETUP.md](FIREBASE_SETUP.md)

### "No Active Project" when viewing members
→ Create or join a project first

### Changes not syncing
→ Check internet connection and Firestore status

### Invalid code when joining
→ Double-check the code format (ABC-123) and verify it's correct

## What Syncs in Real-Time?

✅ File content changes  
✅ User presence (online/offline)  
✅ Currently editing file  
✅ Cursor position  

## Next Steps

1. Set up Firebase (if not done)
2. Create a test project
3. Share code with a friend
4. Test real-time editing together!

For detailed setup instructions, see [FIREBASE_SETUP.md](FIREBASE_SETUP.md)
