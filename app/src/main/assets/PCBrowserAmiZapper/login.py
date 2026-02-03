"""
Amizone Login - Opens Brave browser for manual login and extracts cookies
"""
import json
import time
import undetected_chromedriver as uc

def setup_brave_driver():
    """Setup undetected-chromedriver with Brave browser"""
    
    # Path to Brave browser executable
    brave_path = r"C:\Program Files\BraveSoftware\Brave-Browser\Application\brave.exe"
    
    # Options for the browser
    options = uc.ChromeOptions()
    options.binary_location = brave_path
    
    # Small window size for micro browser
    options.add_argument("--window-size=800,900")
    options.add_argument("--window-position=100,100")
    
    # Create driver - undetected-chromedriver handles version matching automatically
    driver = uc.Chrome(options=options, version_main=144)
    
    return driver

def wait_for_login(driver):
    """Wait for user to complete login and reach Home page"""
    print("\n🔐 Please login manually in the browser window...")
    print("   1. Enter your credentials")
    print("   2. Solve the Cloudflare captcha")
    print("   3. Wait until you reach the Home page\n")
    
    # Wait until URL contains /Home
    while True:
        current_url = driver.current_url
        if "/Home" in current_url:
            print("✅ Login successful! Detected Home page.")
            time.sleep(2)  # Wait a bit to ensure everything loads
            break
        time.sleep(1)

def extract_cookies(driver):
    """Extract cookies from browser session"""
    cookies = driver.get_cookies()
    
    # Convert to dictionary format for easier use
    cookie_dict = {}
    for cookie in cookies:
        cookie_dict[cookie['name']] = cookie['value']
    
    return cookie_dict

def save_cookies(cookie_dict, filename='cookies.json'):
    """Save cookies to JSON file"""
    with open(filename, 'w') as f:
        json.dump(cookie_dict, f, indent=2)
    print(f"💾 Cookies saved to {filename}")

def main():
    print("🚀 Starting Amizone Login Helper...")
    print("=" * 50)
    
    driver = None
    try:
        # Setup and open browser
        driver = setup_brave_driver()
        driver.get("https://s.amizone.net")
        
        # Wait for manual login
        wait_for_login(driver)
        
        # Extract cookies
        print("\n📦 Extracting cookies...")
        cookie_dict = extract_cookies(driver)
        
        # Check if we got the important cookies
        important_cookies = ['ASP.NET_SessionId', '.ASPXAUTH', '__RequestVerificationToken']
        found_cookies = [c for c in important_cookies if c in cookie_dict]
        
        print(f"   Found {len(found_cookies)}/{len(important_cookies)} important cookies")
        
        # Save cookies
        save_cookies(cookie_dict)
        
        print("\n✨ Done! You can now run fetch_timetable.py")
        print("=" * 50)
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        
    finally:
        if driver:
            time.sleep(2)
            driver.quit()
            print("\n🔒 Browser closed.")

if __name__ == "__main__":
    main()
