import json
#we want to parse for accidents that happened to cyclists and store the severity and the location
accidents = []
for year in range(2005,2020):
    print(len(accidents), year)
    with open(f"accidents/{year}.json","r") as outfile:
        data = json.load(outfile)
    for x in data:
        try:
            if x["casualties"]["mode"] == "PedalCycle":
                accidents.append([x["lat"],x["lon"],x["severity"]])
        except:
            if len([person for person in x["casualties"] if person["mode"] == "PedalCycle"]) >= 1:
                accidents.append([x["lat"],x["lon"],x["severity"]])
with open("output.json","w") as outfile:
    json.dump(accidents,outfile)


