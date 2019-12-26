#!/usr/bin/python3
import numpy as np
import cv2
import socket
import sys
import time

if len(sys.argv)<4:
    print('usage : [ ip ] [ port ] [ file name ]')
    sys.exit()
ip=sys.argv[1]
port=int(sys.argv[2])
sock=socket.socket()
sock.connect((ip,port))

name=sys.argv[3]

if len(sys.argv) == 4:
    sock.send(name.encode('utf-8'))
elif len(sys.argv) == 5:
    sock.send((sys.argv[4]+".mp4").encode('utf-8'))

capture=cv2.VideoCapture(name)
success , frame = capture.read()
c=0
st = time.time()
print(st)
while(success):
    c+=1
    print(c)
    _,imgencode=cv2.imencode('.jpg',frame,[int(cv2.IMWRITE_JPEG_QUALITY),90])
    data=np.array(imgencode)
    strdata=data.tostring()
    sock.send(str(len(strdata)).ljust(16).encode('utf-8'))
    sock.send(strdata)
    #if c > 499:
    #    break
    success , frame = capture.read()

capture.release()
cv2.destroyAllWindows()
et = time.time()
print(st, et, et-st)
