import os

def process_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 1. Doi ten app title thanh BHS
    content = content.replace('android:text="Meeting Notes"', 'android:text="BHS"')
    
    # 2. An subtitle duoi app title
    content = content.replace('android:text="G720 AI Box"', 'android:visibility="gone"\n            android:text="G720 AI Box"')
    
    # 3. An subtitle nho o danh sach ban ghi am
    content = content.replace('android:text="@string/home_subtitle"', 'android:visibility="gone"\n                    android:text="@string/home_subtitle"')
    content = content.replace('android:text="Danh sách bản ghi âm và tóm tắt thông minh"', 'android:visibility="gone"\n                    android:text=""')

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Processed:", path)

process_file('app/src/main/res/layout-sw600dp/activity_meeting_list.xml')
if os.path.exists('app/src/main/res/layout/activity_meeting_list.xml'):
    process_file('app/src/main/res/layout/activity_meeting_list.xml')
