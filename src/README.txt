README

How to use:

Change HOST in MainSender to Receiver's IP
Change OUTPUT_FILE in MainReceiver to desired output file name

Example of usage sending testImage.jpg:
Build: javac -d out .\src\protocols\*.java .\src\*.java
Run Receiver: java -cp out MainReceiver .\received_data\testImage_received.jpg 
Run Sender: java -cp out MainSender .\data\testImage.jpg

For SelectiveRepeat ARQ: 

1. Build project with Intellij. (I don't know why jacac doesn't like me.)
2. Start receiver with ` java -cp out\production\project1 MainReceiver`
3. Start sender with `java -cp out\production\project1 MainSender data/testXXX.YYY`
