import json
import matplotlib.pyplot as plt
import numpy as np
x = []
y = []
with open("output.json","r") as inputfile:
    data = json.load(inputfile)
for accident in data:
    x.append(accident[1])
    y.append(accident[0])
    print(accident[0],accident[1])
plt.scatter(x,y,0.05)
plt.show()



