import os
import socket
import sys

import select

ip_addr = sys.argv[2]
ip_port = int(sys.argv[3])
tmp_dir = sys.argv[4]

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.bind((ip_addr, ip_port))
sock.listen(5)
print("PID: %d, Listen at %s:%d" % (os.getpid(), ip_addr, ip_port))

import traceback

import cv2

from src.keras_utils import load_model, detect_lp
from src.utils import im2single


def adjust_pts(pts, lroi):
	return pts * lroi.wh().reshape((2, 1)) + lroi.tl().reshape((2, 1))


def do_predict(img_path):
	Ivehicle = cv2.imread(img_path)

	ratio = float(max(Ivehicle.shape[:2])) / min(Ivehicle.shape[:2])
	side = int(ratio * 288.)
	bound_dim = min(side + (side % (2 ** 4)), 608)
	# print("\t\tBound dim: %d, ratio: %f" % (bound_dim, ratio))

	containsLp = detect_lp(wpod_net, im2single(Ivehicle), bound_dim, 2 ** 4, (240, 80), lp_threshold)

	return containsLp


if __name__ == '__main__':
	wpod_net_path = sys.argv[1]
	inputs = [sock]
	read_buffer = {}
	outputs = []

	try:
		lp_threshold = .5
		wpod_net = load_model(wpod_net_path)
		while inputs:
			readable, _, exceptional = select.select(inputs, outputs, inputs)

			for fd in readable:
				if fd is sock:
					connection, client_address = fd.accept()
					connection.setblocking(0)
					inputs.append(connection)
				else:
					L = 64
					data = []
					if fd in read_buffer:
						data = read_buffer[fd]
					else:
						read_buffer[fd] = data

					if len(data) != L:
						read_data = fd.recv(L - len(data))
						if read_data:
							data.extend(read_data)
						else:
							if fd in outputs:
								outputs.remove(fd)
							inputs.remove(fd)
							fd.close()
							continue

					if len(data) == L:
						del read_buffer[fd]
						file_name = bytes(data).decode('utf-8').strip()
						img_path = tmp_dir + "/" + file_name
						ret = do_predict(img_path)
						if ret:
							fd.send(b'\x01')
						else:
							fd.send(b'\x00')
	except:
		traceback.print_exc()
		sys.exit(1)

	sys.exit(0)
