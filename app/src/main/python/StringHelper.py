import re
from unidecode import unidecode

def remove_special_characters(string):
    str = unidecode(string)
    regex = r'[^A-Za-z0-9\s]+'
    return re.sub(regex, '', str)