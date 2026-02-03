"""
Fetch Timetable - Uses saved cookies to fetch weekly timetable from Amizone
"""
import json
import requests
from bs4 import BeautifulSoup
from datetime import datetime

def load_cookies(filename='cookies.json'):
    """Load cookies from JSON file"""
    try:
        with open(filename, 'r') as f:
            cookies = json.load(f)
        return cookies
    except FileNotFoundError:
        print(f"❌ {filename} not found! Please run login.py first.")
        return None

def fetch_timetable(cookies):
    """Fetch timetable HTML using saved cookies"""
    url = "https://s.amizone.net/TimeTable/Home?X-Requested-With=XMLHttpRequest"
    
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0',
        'Accept': '*/*',
        'Accept-Language': 'en-US,en;q=0.5',
        'Accept-Encoding': 'gzip, deflate, br, zstd',
        'Referer': 'https://s.amizone.net/Home',
        'X-Requested-With': 'XMLHttpRequest',
        'Sec-Fetch-Dest': 'empty',
        'Sec-Fetch-Mode': 'cors',
        'Sec-Fetch-Site': 'same-origin',
    }
    
    print("📡 Fetching timetable from Amizone...")
    
    try:
        response = requests.get(url, headers=headers, cookies=cookies)
        
        if response.status_code == 200:
            print("✅ Timetable fetched successfully!")
            return response.text
        else:
            print(f"❌ Failed to fetch timetable. Status code: {response.status_code}")
            return None
            
    except Exception as e:
        print(f"❌ Error fetching timetable: {e}")
        return None

def parse_timetable(html):
    """Parse timetable HTML and extract schedule information"""
    soup = BeautifulSoup(html, 'html.parser')
    
    print("\n" + "=" * 80)
    print("📅 YOUR WEEKLY TIMETABLE")
    print("=" * 80)
    
    # Find all day sections
    days = soup.find_all('div', class_='day')
    
    if not days:
        # Try alternate parsing - just show the raw table structure
        tables = soup.find_all('table')
        if tables:
            print("\n⚠️  Could not parse specific format. Raw timetable data:")
            print(soup.get_text(separator='\n', strip=True))
        else:
            print("\n⚠️  No timetable data found in response.")
            print("\nHTML Preview:")
            print(html[:500])
        return
    
    # Parse each day
    for day in days:
        day_name = day.find('div', class_='day-name')
        classes = day.find_all('div', class_='class-item')
        
        if day_name:
            print(f"\n📌 {day_name.get_text(strip=True)}")
            print("-" * 80)
        
        if classes:
            for cls in classes:
                time = cls.find('div', class_='time')
                subject = cls.find('div', class_='subject')
                room = cls.find('div', class_='room')
                faculty = cls.find('div', class_='faculty')
                
                time_str = time.get_text(strip=True) if time else "N/A"
                subject_str = subject.get_text(strip=True) if subject else "N/A"
                room_str = room.get_text(strip=True) if room else "N/A"
                faculty_str = faculty.get_text(strip=True) if faculty else "N/A"
                
                print(f"  ⏰ {time_str}")
                print(f"  📚 {subject_str}")
                print(f"  🏫 {room_str}")
                print(f"  👨‍🏫 {faculty_str}")
                print()
        else:
            print("  No classes scheduled\n")
    
    print("=" * 80)

def save_raw_html(html, filename='timetable.html'):
    """Save raw HTML for inspection"""
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(html)
    print(f"\n💾 Raw HTML saved to {filename}")

def main():
    print("🚀 Amizone Timetable Fetcher")
    print("=" * 80)
    
    # Load cookies
    cookies = load_cookies()
    if not cookies:
        return
    
    print(f"✅ Loaded {len(cookies)} cookies from cookies.json")
    
    # Fetch timetable
    html = fetch_timetable(cookies)
    
    if html:
        # Save raw HTML
        save_raw_html(html)
        
        # Parse and display
        parse_timetable(html)
        
        print("\n✨ Done! Check timetable.html for raw data if needed.")
    else:
        print("\n❌ Could not fetch timetable. Your cookies may have expired.")
        print("   Please run login.py again to refresh cookies.")

if __name__ == "__main__":
    main()
