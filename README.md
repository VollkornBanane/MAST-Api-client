# MAST-Api-client
A project of mine that calls MASTs Api with a request and can filter and format the resulting data.

## Requesting the data
The request is stored in request.json. The formatting can be found at https://mast.stsci.edu/api/v0/_services.html

The result of that request is then stored in _output.json_, which isn't formatted or filtered.

## Filter and Formatting the data

The filters and format can be specified in the _filter.json_ file.
For every field that is requested there is a section "format" and "filter".

### Filters
There are multiple ways to filter the data. They can be chosen by using "filterType".

1. "regex-allow": Uses the field "filter" to specify the allowed data.
2. "regex-deny": Uses the field "filter" to specify the disallowed data.
3. "range": Uses the fields "from" and "to" to specify the range of allowed values
4. "none": doesn't filter out anything

### Formatting
Formatting works the same as the filters. Akin to them, the formatting is specified with "formatType".

1. "surround": Adds a "prefix" and "suffix" to the data
1. "date": Uses the field "formatting" to specify a dateformat
1. "fileSize": Can display filesizes in MB or KB as specified in the field "formatting"

## Output
The output of the data is stored in _response.csv_.
