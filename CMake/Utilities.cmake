# only fetch target repo for add_subdirectory later
function(fetch_repo lib_name)
  set(supported_libs
      kvspic)
  list(FIND supported_libs ${lib_name} index)
  if(${index} EQUAL -1)
    message(WARNING "${lib_name} is not supported for fetch_repo")
    return()
  endif()

  if (WIN32 OR NOT PARALLEL_BUILD)
    set(PARALLEL_BUILD "")  # No parallel build for Windows
  else()
    set(PARALLEL_BUILD "--parallel")  # Enable parallel builds for Unix-like systems
  endif()

  # build library
  configure_file(
    ./CMake/Dependencies/lib${lib_name}-CMakeLists.txt
    ${DEPENDENCY_DOWNLOAD_PATH}/lib${lib_name}/CMakeLists.txt COPYONLY)
  execute_process(
    COMMAND ${CMAKE_COMMAND}
            ${CMAKE_GENERATOR} .
    RESULT_VARIABLE result
    WORKING_DIRECTORY ${DEPENDENCY_DOWNLOAD_PATH}/lib${lib_name})
  if(result)
    message(FATAL_ERROR "CMake step for lib${lib_name} failed: ${result}")
  endif()
  execute_process(
    COMMAND ${CMAKE_COMMAND} --build . ${PARALLEL_BUILD}
    RESULT_VARIABLE result
    WORKING_DIRECTORY ${DEPENDENCY_DOWNLOAD_PATH}/lib${lib_name})
  if(result)
    message(FATAL_ERROR "CMake step for lib${lib_name} failed: ${result}")
  endif()
endfunction()

function(enableSanitizer SANITIZER)
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O0 -g -fsanitize=${SANITIZER} -fno-omit-frame-pointer" PARENT_SCOPE)
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O0 -g -fsanitize=${SANITIZER} -fno-omit-frame-pointer -fno-optimize-sibling-calls" PARENT_SCOPE)
  set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -fsanitize=${SANITIZER}" PARENT_SCOPE)
endfunction()
