import requests
import json
for year in range(2005,2020):
    print(year)
    response = requests.get(f"https://api.tfl.gov.uk/AccidentStats/{year}")
    with open(f"accidents/{year}.json","w") as yearfile:
        json.dump(response.json(),yearfile)
    
