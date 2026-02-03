# AmiZapper - Amizone Timetable Fetcher

A lightweight Python tool to fetch your weekly timetable from Amizone (s.amizone.net) with manual Cloudflare captcha solving.

## Setup

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Make sure Brave browser is installed at:
```
C:\Program Files\BraveSoftware\Brave-Browser\Application\brave.exe
```

## Usage

### Step 1: Login and Extract Cookies
```bash
python login.py
```
- A small Brave window will open
- Login to Amizone manually
- Solve the Cloudflare captcha
- Wait until you reach the Home page
- Script will automatically extract and save cookies

### Step 2: Fetch Timetable
```bash
python fetch_timetable.py
```
- Uses saved cookies to fetch your weekly timetable
- Displays schedule in terminal
- Saves raw HTML to `timetable.html`

## Files

- `login.py` - Opens browser for manual login, extracts cookies
- `fetch_timetable.py` - Fetches and parses timetable using saved cookies
- `cookies.json` - Stored session cookies (auto-generated, gitignored)
- `timetable.html` - Raw timetable HTML (auto-generated)

## Notes

- Cookies expire after some time, re-run `login.py` if fetch fails
- Raw HTML is saved for debugging/custom parsing
- Keep `cookies.json` private (already in .gitignore)
