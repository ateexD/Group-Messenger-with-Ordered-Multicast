for i in {1..10}
do
    ./groupmessenger2-grading.osx /Users/ateendraramesh/Downloads/app-debug.apk
done

for i in "5554" "5556" "5558" "5560" "5562"
do
    adb -s "emulator-"$i  uninstall edu.buffalo.cse.cse486586.groupmessenger2
done
