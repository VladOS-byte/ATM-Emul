# ATM-Emul

iRZ ATM modem emulator. Work with iRZ Collector

## Install 

1. Clone or download repository.
2. Download JRE / JDK 11 (11.0.4 +)
3. Install Java 11
4. Set Java into PATH
5. Change directory to TestModem.2020
6. Run program

~~~
java TestModem
~~~

7. Enjoy

## Usage

~~~
java TestModem -help
~~~

Usage: java TestModem [-option argument]

### Options: 

-m <letter in upper case - mode of working program>

-c <integer - count of virtual devices>

-d <long integer - difference between IMEI of start device and 1_000_000_000_000_000>

-t <integer - timeout between creating of devices

-s <string - name of settings file in "set" folder>

## Documentation 

https://www.overleaf.com/read/wqkhyrnzpjvf

