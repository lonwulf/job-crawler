# Job Crawler
- Configure your `.env` file with `API_KEY`, `CX`, and `INTERVAL_HOURS` values
- `API_KEY` can be setup and retrieved here [Custom search](https://developers.google.com/custom-search/v1/overview)
- `CX` id can be setup and retrieved here [Programmable Search Engine ID](https://programmablesearchengine.google.com/controlpanel/all)

## Search configurations:
- `INTERVAL_HOURS` value that triggers the next scheduled application run
- In `docker-compose.yml`, `TITLES` can be given whichever params you want to execute when querying the search API and can also modify the location boundaries `LOCATIONS`
- Currently, the files are exported to Documents directory which is also customizable in the `docker-compose.yml`

### Other customizations 
- Currently, in the `docker-compose.yml` file, you can modify and exclude the titles you do not want included in the results at `EXCLUDE_REGEXES` and specify the role you want to filter `ROLE`


**GO NUTS**