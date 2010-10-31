const int CMD_LEN = 5;
const byte MSG_HEADER = 1;
const byte MSG_FOOTER = 4;

const byte P_HIGH = 1;
const byte P_LOW = 0;

const byte PIN_DIGITAL = 1;
const byte PWM = 2;
const byte ANALOG_READ = 3;

void setup(){
  Serial.begin(9600);
}

void sendInt(int toSend){
  Serial.write(MSG_HEADER);
  Serial.write(4);
  Serial.write(toSend >> 24 & 0xFF);
  Serial.write(toSend >> 16 & 0xFF);
  Serial.write(toSend >> 8 & 0xFF);
  Serial.write(toSend >> 0 & 0xFF);
}

void loop(){
  int i, pinName, level;
  if(Serial.available() >= CMD_LEN){
    //--Get the Command
    byte* cmd = (byte*) malloc(CMD_LEN * sizeof(byte));
    for(i=0; i<CMD_LEN; i++){
      cmd[i] = Serial.read();
    }  
    //--Route the Command
    switch(cmd[0]){
      //(digital write)
      case PIN_DIGITAL:
        pinName = (int) cmd[1];
        if(pinName < 0 || pinName > 13){ Serial.println("PIN_DIGITAL: Invalid pin number"); }
        pinMode(pinName, OUTPUT); 
        switch(cmd[2]){
          case P_HIGH:
            digitalWrite(pinName, HIGH);
            break;
          case P_LOW:
            digitalWrite(pinName, LOW);
            break;
          default:
            Serial.println("PIN_DIGITAL: Unknown pin state");
            break;
        }
        break;
        
      //(pwm)
      case PWM:
        pinName = (int) cmd[1];
        level = (int) cmd[2];
        if(pinName < 0 || pinName > 13){ Serial.println("PWM: Invalid pin number"); }
        if(level < 0) Serial.println("PWM: frequency is negative");
        if(level > 255) Serial.println("PWM: frequency overflows a byte");
        pinMode(pinName, OUTPUT); 
        analogWrite(pinName, level);
        break;
      
      //(analog read)
      case ANALOG_READ:
        pinName = (int) cmd[1];
        if(pinName < 0 || pinName > 5){ Serial.println("ANALOG_READ: Invalid pin number"); }
        pinName = pinName + 14;
        if(level < 0) Serial.println("PWM: frequency is negative");
        if(level > 255) Serial.println("PWM: frequency overflows a byte");
        pinMode(pinName, INPUT);
        level = analogRead(pinName); 
        sendInt(level);
        break;
      
      //(error)
      default:
        Serial.println("UNKNOWN COMMAND");
        break;
        
    }
    free(cmd);
  }
}







