# Cập nhật sidebar cho layout-sw600dp và layout thông thường
# Yêu cầu người dùng:
# 1. "Meeting Notes thì đổi tên thành BHS" -> đã đổi
# 2. "bỏ phần chữ nhỏ danh sách bản ghi âm và tóm tắt thông minh" -> đã ẩn
# 3. "các phần tất cả tiếng việt english thùng rác cho tôi đổi ico như tất cả"
# 4. "và phần tiếng việc và english là cờ cho tôi" -> icon cờ VN & cờ EN
# 5. "bỏ các phần chữ tiếng việt english và bỏ chữ thùng rác luôn cho tôi"
# 6. "và thu gọn vào bên trong thư mục để cảm giác phân cấp thư mục thì có tiếng việt và english sau này có thể mở rộng thêm thứ tiếng khác hoặc phân cấp khác và thùng rác ở bên ngoài sẽ hiển thị được đẹp nhất"

import re

def update_sidebar(path):
    with open(path, 'r', encoding='utf-8') as f:
        xml = f.read()

    # Tạo cụm sidebar mới:
    # 1. 'Tất cả' (Root folder/All) có icon ic_folder, chữ "Tất cả", badge count
    # 2. Nhóm phân cấp (Sub-folder container) có đường viền nhẹ hoặc thụt lề (marginStart: 16dp / paddingStart)
    #    - Đoạn Vi: Icon cờ Việt Nam (ic_flag_vn), không có chữ (chỉ icon cờ + badge count bên phải)
    #    - Đoạn En: Icon cờ Anh (ic_flag_en), không có chữ (chỉ icon cờ + badge count bên phải)
    # 3. 'Thùng rác': ở ngoài phân cấp, chỉ có icon ic_delete + badge count, bỏ chữ "Thùng rác"
    
    # Chúng ta thay thế toàn bộ khối <LinearLayout android:id="@+id/ll_sidebar_filters" ... </LinearLayout>
    
    new_filters_block = '''        <LinearLayout
            android:id="@+id/ll_sidebar_filters"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/space_l"
            android:orientation="vertical"
            android:paddingHorizontal="@dimen/space_l"
            app:layout_constraintTop_toBottomOf="@id/tv_app_subtitle">

            <!-- 1. Cấp gốc: Tất cả cuộc họp -->
            <LinearLayout
                android:id="@+id/nav_all"
                android:layout_width="match_parent"
                android:layout_height="@dimen/touch_primary"
                android:background="@drawable/bg_circle_blue_ring"
                android:contentDescription="@string/home_filter_all"
                android:clickable="true"
                android:focusable="true"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingHorizontal="@dimen/space_m">

                <ImageView
                    android:layout_width="@dimen/icon_l"
                    android:layout_height="@dimen/icon_l"
                    android:src="@drawable/ic_folder"
                    app:tint="@color/blue_primary" />

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_marginStart="14dp"
                    android:text="Tất cả"
                    android:textColor="@color/blue_primary"
                    android:textSize="@dimen/text_section"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tv_count_all"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:background="@drawable/bg_circle_blue"
                    android:paddingHorizontal="12dp"
                    android:paddingVertical="4dp"
                    android:text="0"
                    android:textColor="@color/bg_card"
                    android:textSize="@dimen/text_caption"
                    android:textStyle="bold" />
            </LinearLayout>

            <!-- 2. Nhánh phân cấp thư mục ngôn ngữ (Sub-folders) thụt lề 16dp -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginStart="16dp"
                android:layout_marginTop="4dp"
                android:orientation="vertical">

                <!-- Phân cấp: Tiếng Việt (Icon cờ VN, bỏ chữ, badge số lượng) -->
                <LinearLayout
                    android:id="@+id/nav_vi"
                    android:layout_width="match_parent"
                    android:layout_height="48dp"
                    android:layout_marginTop="4dp"
                    android:contentDescription="@string/home_filter_vi"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center_vertical"
                    android:orientation="horizontal"
                    android:paddingHorizontal="@dimen/space_m">

                    <ImageView
                        android:layout_width="26dp"
                        android:layout_height="20dp"
                        android:src="@drawable/ic_flag_vn" />

                    <!-- Spacing / Expanding space -->
                    <View
                        android:layout_width="0dp"
                        android:layout_height="1dp"
                        android:layout_weight="1" />

                    <TextView
                        android:id="@+id/tv_count_vi"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:background="@drawable/bg_circle_gray"
                        android:paddingHorizontal="10dp"
                        android:paddingVertical="3dp"
                        android:text="0"
                        android:textColor="@color/text_secondary"
                        android:textSize="@dimen/text_caption"
                        android:textStyle="bold" />
                </LinearLayout>

                <!-- Phân cấp: English (Icon cờ Anh, bỏ chữ, badge số lượng) -->
                <LinearLayout
                    android:id="@+id/nav_en"
                    android:layout_width="match_parent"
                    android:layout_height="48dp"
                    android:layout_marginTop="4dp"
                    android:contentDescription="@string/home_filter_en"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center_vertical"
                    android:orientation="horizontal"
                    android:paddingHorizontal="@dimen/space_m">

                    <ImageView
                        android:layout_width="26dp"
                        android:layout_height="20dp"
                        android:src="@drawable/ic_flag_en" />

                    <!-- Spacing / Expanding space -->
                    <View
                        android:layout_width="0dp"
                        android:layout_height="1dp"
                        android:layout_weight="1" />

                    <TextView
                        android:id="@+id/tv_count_en"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:background="@drawable/bg_circle_gray"
                        android:paddingHorizontal="10dp"
                        android:paddingVertical="3dp"
                        android:text="0"
                        android:textColor="@color/text_secondary"
                        android:textSize="@dimen/text_caption"
                        android:textStyle="bold" />
                </LinearLayout>
            </LinearLayout>

            <!-- 3. Thùng rác bên ngoài nhánh phân cấp (Chỉ icon Thùng rác + Badge số lượng, bỏ chữ) -->
            <LinearLayout
                android:id="@+id/nav_trash"
                android:layout_width="match_parent"
                android:layout_height="@dimen/touch_primary"
                android:layout_marginTop="@dimen/space_s"
                android:contentDescription="@string/home_filter_trash"
                android:clickable="true"
                android:focusable="true"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingHorizontal="@dimen/space_m">

                <ImageView
                    android:id="@+id/iv_nav_trash"
                    android:layout_width="@dimen/icon_l"
                    android:layout_height="@dimen/icon_l"
                    android:src="@drawable/ic_delete"
                    app:tint="@color/text_secondary" />

                <!-- Spacing / Expanding space thay vì chữ -->
                <View
                    android:layout_width="0dp"
                    android:layout_height="1dp"
                    android:layout_weight="1" />

                <TextView
                    android:id="@+id/tv_count_trash"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:background="@drawable/bg_circle_gray"
                    android:paddingHorizontal="12dp"
                    android:paddingVertical="4dp"
                    android:text="0"
                    android:textColor="@color/text_secondary"
                    android:textSize="@dimen/text_caption"
                    android:textStyle="bold" />
            </LinearLayout>
        </LinearLayout>'''

    pattern = r'<LinearLayout\s+android:id="@+id/ll_sidebar_filters".*?<!-- Big Animated Mic Button'
    sub_replacement = new_filters_block + '\n\n        <!-- Big Animated Mic Button'
    new_xml, count = re.subn(pattern, sub_replacement, xml, flags=re.DOTALL)
    if count > 0:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(new_xml)
        print("Updated sidebar filters in:", path)
    else:
        print("Pattern match failed for:", path)

update_sidebar('app/src/main/res/layout-sw600dp/activity_meeting_list.xml')
update_sidebar('app/src/main/res/layout/activity_meeting_list.xml')
