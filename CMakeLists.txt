cmake_minimum_required(VERSION 3.6.3)

set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/CMake;${CMAKE_MODULE_PATH}")
include(Utilities)

project(KinesisVideoProducerJNI VERSION 3.4.2)

set(CMAKE_CXX_STANDARD 11)
include(GNUInstallDirs)

add_definitions(-DCPP_VERSION_STRING=\"${PROJECT_VERSION}\")

set(CMAKE_MACOSX_RPATH TRUE)

############# Check dependencies ############

find_package(Threads)

find_package(JNI QUIET)
if(NOT JNI_FOUND)
    message(FATAL_ERROR
        "\nJava Development Kit (JDK) not found!"
        "\nPlease ensure that:"
        "\n  1. JDK is installed"
        "\n  2. JAVA_HOME environment variable is set correctly"
        "\n  3. Your JDK installation includes JNI headers"
    )
endif()
include_directories(${JNI_INCLUDE_DIRS})

############# Enable Sanitizers ############
if(${CMAKE_C_COMPILER_ID} MATCHES "GNU|Clang")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC")

  if(ADD_MUCLIBC)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -muclibc")
  endif()

  if(CODE_COVERAGE)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O0 -g -fprofile-arcs -ftest-coverage")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} --coverage")
  endif()

  if(ADDRESS_SANITIZER)
    enableSanitizer("address")
  endif()
  if(MEMORY_SANITIZER)
    enableSanitizer("memory")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fsanitize-memory-track-origins")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fsanitize-memory-track-origins")
  endif()
  if(THREAD_SANITIZER)
    enableSanitizer("thread")
  endif()
  if(UNDEFINED_BEHAVIOR_SANITIZER)
    enableSanitizer("undefined")
  endif()
endif()

if(MSVC)
  add_definitions(-D_CRT_SECURE_NO_WARNINGS -D_CRT_NONSTDC_NO_WARNINGS -D_SILENCE_TR1_NAMESPACE_DEPRECATION_WARNING)
endif()

############# Build Targets ############

# PIC
set(DEPENDENCY_DOWNLOAD_PATH ${CMAKE_CURRENT_SOURCE_DIR}/dependency)
set(BUILD_ARGS -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE})
fetch_repo(kvspic ${BUILD_ARGS})
add_subdirectory("${DEPENDENCY_DOWNLOAD_PATH}/libkvspic/kvspic-src")
file(GLOB PIC_HEADERS "${pic_project_SOURCE_DIR}/src/*/include")
include_directories("${PIC_HEADERS}")

# JNI
file(GLOB_RECURSE JNI_SOURCE_FILES "jni/*.cpp")
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/jni/include)

add_library(KinesisVideoProducerJNI SHARED ${JNI_HEADERS} ${JNI_SOURCE_FILES})
target_link_libraries(KinesisVideoProducerJNI kvspic)

add_custom_target(clean-all
    COMMENT "Removing all build and dependency directories"
    COMMAND ${CMAKE_COMMAND} -E remove_directory ${CMAKE_BINARY_DIR}
    COMMAND ${CMAKE_COMMAND} -E remove_directory ${DEPENDENCY_DOWNLOAD_PATH}
    COMMENT "Removed the build and dependency directories"
)
