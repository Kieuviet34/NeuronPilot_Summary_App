cmake_minimum_required(VERSION 3.4.1)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/include/utils
        ${CMAKE_CURRENT_SOURCE_DIR}/include/common
        ${CMAKE_CURRENT_SOURCE_DIR}/include/tokenizer
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/include)

add_library(mtk_llm SHARED IMPORTED)
set_target_properties(mtk_llm PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libmtk_llm.so)

add_library(mtk_common SHARED IMPORTED)
set_target_properties(mtk_common PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libcommon.so)

add_library(mtk_tokenizer SHARED IMPORTED)
set_target_properties(mtk_tokenizer PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libtokenizer.so)

add_library(yaml-cpp SHARED IMPORTED)
set_target_properties(yaml-cpp PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libyaml-cpp.so)

add_library(sentencepiece SHARED IMPORTED)
set_target_properties(sentencepiece PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libsentencepiece.so)

add_library(hf_tokenizer SHARED IMPORTED)
set_target_properties(hf_tokenizer PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libhf-tokenizer.so)

add_library(re2 SHARED IMPORTED)
set_target_properties(re2 PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libre2.so)

#add_library(apu_mdw SHARED IMPORTED)
#set_target_properties(apu_mdw PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libapu_mdw.so)

add_library(neuron_adapter SHARED IMPORTED)
set_target_properties(neuron_adapter PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libneuron_adapter.so)

add_library(neuron_runtime SHARED IMPORTED)
set_target_properties(neuron_runtime PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libneuron_runtime.so)

add_library(nn_sample
        SHARED
        nn_sample.cpp
        simple_model.cpp
        utils.cpp
        NPUWareUtilsLib.cpp)

target_link_libraries(nn_sample
        android
        log
        mtk_llm
        mtk_common
        mtk_tokenizer
        yaml-cpp
        sentencepiece
        hf_tokenizer
        re2
        neuron_runtime
        neuron_adapter)
