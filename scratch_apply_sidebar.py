with open('app/src/main/res/layout-sw600dp/activity_meeting_list.xml', 'r', encoding='utf-8') as f:
    text = f.read()

start_marker = '        <!-- Filters -->'
end_marker = '        <!-- Big Animated Mic Button (No text, pulsing rings) -->'

p1 = text.find(start_marker)
p2 = text.find(end_marker)

new_filters_block = '''        <!-- Filters: Thư mục phân cấp và Thùng rác -->
        <LinearLayout
            android:id="@+id/ll_sidebar_filters"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:orientation="vertical"
            android:paddingHorizontal="14dp"
            app:layout_constraintTop_toBottomOf="@id/tv_app_subtitle">

            <!-- 1. Cấp gốc: Tất cả cuộc họp (Folder chính) -->
            <LinearLayout
                android:id="@+id/nav_all"
                android:layout_width="match_parent"
                android:layout_height="52dp"
                android:background="@drawable/bg_circle_blue_ring"
                android:contentDescription="@string/home_filter_all"
                android:clickable="true"
                android:focusable="true"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingHorizontal="14dp">

                <ImageView
                    android:layout_width="26dp"
                    android:layout_height="26dp"
                    android:src="@drawable/ic_folder"
                    app:tint="@color/blue_primary" />

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_marginStart="14dp"
                    android:text="Tất cả"
                    android:textColor="@color/blue_primary"
                    android:textSize="17sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tv_count_all"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:background="@drawable/bg_circle_blue"
                    android:paddingHorizontal="10dp"
                    android:paddingVertical="3dp"
                    android:text="0"
                    android:textColor="@color/bg_card"
                    android:textSize="13sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <!-- 2. Nhánh phân cấp thư mục ngôn ngữ (Sub-folders thụt lề 20dp, icon cờ, không chữ) -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginStart="20dp"
                android:layout_marginTop="6dp"
                android:orientation="vertical">

                <!-- Phân cấp: Tiếng Việt (Cờ VN + Badge số lượng, không text) -->
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
                    android:paddingHorizontal="12dp">

                    <ImageView
                        android:layout_width="28dp"
                        android:layout_height="20dp"
                        android:src="@drawable/ic_flag_vn" />

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
                        android:textSize="13sp"
                        android:textStyle="bold" />
                </LinearLayout>

                <!-- Phân cấp: English (Cờ Anh + Badge số lượng, không text) -->
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
                    android:paddingHorizontal="12dp">

                    <ImageView
                        android:layout_width="28dp"
                        android:layout_height="20dp"
                        android:src="@drawable/ic_flag_en" />

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
                        android:textSize="13sp"
                        android:textStyle="bold" />
                </LinearLayout>
            </LinearLayout>

            <!-- Đường phân cách nhẹ giữa thư mục và thùng rác -->
            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:layout_marginVertical="10dp"
                android:background="@color/border_light" />

            <!-- 3. Thùng rác ở bên ngoài phân cấp (Chỉ icon Thùng rác + Badge số lượng, không text) -->
            <LinearLayout
                android:id="@+id/nav_trash"
                android:layout_width="match_parent"
                android:layout_height="52dp"
                android:contentDescription="@string/home_filter_trash"
                android:clickable="true"
                android:focusable="true"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingHorizontal="14dp">

                <ImageView
                    android:id="@+id/iv_nav_trash"
                    android:layout_width="26dp"
                    android:layout_height="26dp"
                    android:src="@drawable/ic_delete"
                    app:tint="@color/text_secondary" />

                <View
                    android:layout_width="0dp"
                    android:layout_height="1dp"
                    android:layout_weight="1" />

                <TextView
                    android:id="@+id/tv_count_trash"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:background="@drawable/bg_circle_gray"
                    android:paddingHorizontal="10dp"
                    android:paddingVertical="3dp"
                    android:text="0"
                    android:textColor="@color/text_secondary"
                    android:textSize="13sp"
                    android:textStyle="bold" />
            </LinearLayout>
        </LinearLayout>
'''

new_text = text[:p1] + new_filters_block + '\n' + text[p2:]
with open('app/src/main/res/layout-sw600dp/activity_meeting_list.xml', 'w', encoding='utf-8') as f:
    f.write(new_text)

print('Updated layout-sw600dp successfully!')

# Làm tương tự cho layout/activity_meeting_list.xml nếu có
with open('app/src/main/res/layout/activity_meeting_list.xml', 'r', encoding='utf-8') as f:
    text_default = f.read()

pd1 = text_default.find(start_marker)
pd2 = text_default.find(end_marker)
if pd1 != -1 and pd2 != -1:
    new_default = text_default[:pd1] + new_filters_block + '\n' + text_default[pd2:]
    with open('app/src/main/res/layout/activity_meeting_list.xml', 'w', encoding='utf-8') as f:
        f.write(new_default)
    print('Updated layout/activity_meeting_list.xml successfully!')
