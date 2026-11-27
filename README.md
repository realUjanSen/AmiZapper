# AmiZapper Android App

A clean, feature-rich Android application to view your Amizone timetable with ease.
Designed with a classic Android Holo (ICS 4.0) inspired look.

## Features

### Core Features
- **WebView Login**: Built-in browser for captcha solving
- **Cookie Management**: Persistent login session - no need to login repeatedly
- **Weekly Timetable**: View your complete weekly class schedule
- **Full Week View**: See all 7 days at once with horizontal scrolling
- **Home Screen Widget**: Quick access to today's schedule from your home screen

### User Interface
- **Classic Android Holo Blue Theme**: Clean ICS 4.0 inspired design with Holo blue accents
- **Day Tabs**: Quick navigation between Monday-Sunday
- **Auto-select Current Day**: Opens to today's schedule automatically
- **Period Slots**: Shows free periods between classes (no trailing empty periods)
- **Lunch Break**: Visual indicator for lunch/tiffin break (12:50 - 13:50)
- **Class Cards** showing:
  - Time slot (start and end time)
  - Course code with credits
  - Teacher name and room location on same line
  - Attendance status indicator (color bar)
- **Real-time Indicator**: Red line shows current time position in schedule
- **Empty State**: Clean message when no classes are scheduled

### Widget Features
- Shows today's classes on home screen
- Navigate to previous/next days
- Refresh button to update data
- Tap to open full app
- Displays class times, subjects, teachers, and locations

### Period Structure
- 8 periods per day maximum
- Each class: 50 minutes
- Gap between classes: 5 minutes
- Lunch break: 12:50 - 13:50
- Day ends: 5:25 PM
- Only shows periods up to the last scheduled class (no trailing empty periods)

### Authentication
1. Launch app → Login screen
2. Enter your Amizone credentials
3. Click "Login" → WebView opens with Amizone login page
4. Solve captcha and wait
5. App automatically extracts cookies and navigates to timetable
6. Stay logged in - cookies persist between sessions

### Menu Options
- **Full Week**: View entire week's timetable in a grid (landscape mode supported)
- **Refresh**: Manually refresh timetable data
- **Logout**: Clear session and return to login

## Technical Stack

- **Language**: Java
- **UI Framework**: Material Design Components
- **Network**: OkHttp for HTTP requests
- **HTML Parsing**: Jsoup
- **WebView**: For captcha-enabled login
- **Data Persistence**: SharedPreferences for cookie storage

## Dependencies

```kotlin
// Network
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// HTML Parsing
implementation("org.jsoup:jsoup:1.17.2")

// UI Components
implementation("com.google.android.material:material:1.13.0")
implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
```

## How It Works

1. **Login Flow**:
   - User enters their Amizone credentials
   - WebView opens Amizone login page
   - User solves captcha
   - App detects successful login (URL contains "/Home")
   - Extracts and saves cookies (ASP.NET_SessionId, .ASPXAUTH)

2. **Timetable Fetching**:
   - Makes authenticated request to: `https://s.amizone.net/TimeTable/Home`
   - Uses saved cookies for authentication
   - Parses HTML response with Jsoup
   - Extracts class data from timetable boxes

3. **Data Structure**:
   ```
   - Day tabs (Mon-Sun)
   - Each day contains multiple classes
   - Each class has: time, course code, teacher, location, attendance status
   ```

4. **Widget Updates**:
   - Reads timetable data from SharedPreferences
   - Updates when navigating days or pressing refresh
   - Shows current day by default

## File Structure

```
app/src/main/java/com/example/amizapper/
├── MainActivity.java              # Main timetable display with ViewPager
├── LoginActivity.java             # Login screen
├── WebViewLoginActivity.java      # WebView for captcha login
├── WeekViewActivity.java          # Full week grid view
├── TimetableClass.java            # Data model for a class
├── TimetableFetcher.java          # Fetches and parses timetable
├── CookieManager.java             # Cookie storage and retrieval
├── TimetableStorage.java          # Persistent storage for timetable
├── TimetableWidgetProvider.java   # Widget provider
├── TimetableWidgetService.java    # Widget data service
├── DayPagerAdapter.java           # ViewPager adapter for days
├── CurrentTimeIndicator.java      # Real-time indicator
└── SubjectMapper.java             # Maps subject codes to full names

app/src/main/res/
├── layout/
│   ├── activity_main.xml          # Main timetable view
│   ├── activity_login.xml         # Login screen
│   ├── activity_webview_login.xml # WebView screen
│   ├── activity_week_view.xml     # Weekly grid view
│   ├── fragment_day_timetable.xml # Single day view
│   ├── item_timetable_class.xml   # Class card item
│   ├── item_free_period.xml       # Free period card
│   ├── item_lunch_break.xml       # Lunch break card
│   ├── widget_timetable.xml       # Widget layout
│   └── widget_class_item.xml      # Widget class item
├── menu/
│   └── main_menu.xml              # App menu
└── values/
    ├── colors.xml                 # Color scheme (Holo Blue theme)
    ├── strings.xml                # App strings
    └── themes.xml                 # Material Design theme
```

## Features Comparison with PC Version

| Feature | PC Browser Version | Android App |
|---------|-------------------|-------------|
| Login | Manual browser interaction | Integrated WebView |
| Captcha | Manual solving | Manual solving in WebView |
| Credentials | Command line input | Secure text input with show/hide |
| Timetable View | Terminal text | Beautiful Material Design UI |
| Data Refresh | Re-run script | Automatic refresh |
| Session | Cookies in file | Persistent SharedPreferences |
| Day Navigation | Scroll through text | Tab-based swipe navigation |
| Widget Support | None | Home screen widget |
| Landscape Mode | Not applicable | Full-screen weekly view |

## Future Enhancements

- Class reminders/notifications
- Attendance tracking integration
- Assignment deadlines
- Custom color themes
- Offline caching improvements
- Calendar integration
- Quick settings widget controls

## Build & Run

1. Open project in Android Studio
2. Sync Gradle
3. Run on device/emulator (API 24+)
4. Login with your Amizone credentials
5. Solve captcha
6. View your timetable!
7. Add widget to home screen for quick access

## Security & Privacy

- Credentials are never stored in the app
- Only session cookies are saved locally
- All communication uses HTTPS
- Data stored only on device (SharedPreferences)
- No analytics or tracking
- Completely offline after initial login

## Credits

Developed as a university project to provide better timetable access.
Inspired by the PC browser automation version.
